/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.inmobi.databus.local;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.PathFilter;
import org.apache.hadoop.io.DataInputBuffer;
import org.apache.hadoop.io.SequenceFile;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.lib.output.NullOutputFormat;
import org.apache.hadoop.tools.DistCpConstants;

import com.inmobi.databus.AbstractService;
import com.inmobi.databus.CheckpointProvider;
import com.inmobi.databus.Cluster;
import com.inmobi.databus.ConfigConstants;
import com.inmobi.databus.DatabusConfig;
import com.inmobi.databus.DatabusConstants;
import com.inmobi.databus.utils.FileUtil;


/*
 * Handles Local Streams for a Cluster
 * Assumptions
 * (i) One LocalStreamService per Cluster
 */

public class LocalStreamService extends AbstractService implements
ConfigConstants {

  private static final Log LOG = LogFactory.getLog(LocalStreamService.class);

  private final Cluster srcCluster;
  private Cluster currentCluster = null;
  private Path tmpPath;
  private Path tmpJobInputPath;
  private Path tmpJobOutputPath;
  private final int FILES_TO_KEEP = 6;

  // The amount of data expected to be processed by each mapper, such that
  // each map task completes within ~20 seconds. This calculation is based
  // on assumption that the map task processing throughput is ~25 MB/s.
  protected long BYTES_PER_MAPPER = 512 * 1024 * 1024;
  private final ByteArrayOutputStream buffer = new ByteArrayOutputStream(64);
  private DataInputBuffer in = new DataInputBuffer();
  // these paths are used to set the path of input format jar in job conf
  private final Path jarsPath;
  final Path inputFormatJarDestPath;

  public LocalStreamService(DatabusConfig config, Cluster srcCluster,
      Cluster currentCluster, CheckpointProvider provider,
      Set<String> streamsToProcess) throws IOException {
    super("LocalStreamService_" + srcCluster + "_"
        + getServiceName(streamsToProcess), config,
        DEFAULT_RUN_INTERVAL,
        provider, streamsToProcess);
    this.srcCluster = srcCluster;
    if (currentCluster == null)
      this.currentCluster = srcCluster;
    else
      this.currentCluster = currentCluster;
    this.tmpPath = new Path(srcCluster.getTmpPath(), getName());
    this.tmpJobInputPath = new Path(tmpPath, "jobIn");
    this.tmpJobOutputPath = new Path(tmpPath, "jobOut");
    jarsPath = new Path(srcCluster.getTmpPath(), "jars");
    inputFormatJarDestPath = new Path(jarsPath, "hadoop-distcp-current.jar");
  }



  private void cleanUpTmp(FileSystem fs) throws Exception {
    if (fs.exists(tmpPath)) {
      LOG.info("Deleting tmpPath recursively [" + tmpPath + "]");
      fs.delete(tmpPath, true);
    }
  }

  @Override
  public long getMSecondsTillNextRun(long currentTime) {
    return (long) (DEFAULT_RUN_INTERVAL - (long) (currentTime % DEFAULT_RUN_INTERVAL));
  }

  @Override
  protected void execute() throws Exception {
    try {

      FileSystem fs = FileSystem.get(srcCluster.getHadoopConf());
      // Cleanup tmpPath before everyRun to avoid
      // any old data being used in this run if the old run was aborted
      cleanUpTmp(fs);
      LOG.info("TmpPath is [" + tmpPath + "]");
      long commitTime = srcCluster.getCommitTime();

      publishMissingPaths(fs,
          srcCluster.getLocalFinalDestDirRoot(), commitTime, streamsToProcess);

      Map<FileStatus, String> fileListing = new TreeMap<FileStatus, String>();
      Set<FileStatus> trashSet = new HashSet<FileStatus>();
      // checkpointKey, CheckPointPath
      Map<String, FileStatus> checkpointPaths = new TreeMap<String, FileStatus>();

      long totalSize = createMRInput(tmpJobInputPath, fileListing, trashSet,
          checkpointPaths);

      if (fileListing.size() == 0) {
        LOG.info("Nothing to do!");
        return;
      }
      Job job = createJob(tmpJobInputPath, totalSize);
      job.waitForCompletion(true);
      if (job.isSuccessful()) {
        commitTime = srcCluster.getCommitTime();
        LOG.info("Commiting mvPaths and ConsumerPaths");
        commit(prepareForCommit(commitTime));
        checkPoint(checkpointPaths);
        LOG.info("Commiting trashPaths");
        commit(populateTrashCommitPaths(trashSet));
        LOG.info("Committed successfully at " + getLogDateString(commitTime));
      }
    } catch (Exception e) {
      LOG.warn("Error in running LocalStreamService " + e);
      throw e;
    }
  }

  private void checkPoint(Map<String, FileStatus> checkPointPaths) {
    Set<Entry<String, FileStatus>> entries = checkPointPaths.entrySet();
    for (Entry<String, FileStatus> entry : entries) {
      String value = entry.getValue().getPath().getName();
      LOG.debug("Check Pointing Key [" + entry.getKey() + "] with value ["
          + value + "]");
      checkpointProvider.checkpoint(entry.getKey(), value.getBytes());
    }
  }

  Map<Path, Path> prepareForCommit(long commitTime) throws Exception {
    FileSystem fs = FileSystem.get(srcCluster.getHadoopConf());

    // find final destination paths
    Map<Path, Path> mvPaths = new LinkedHashMap<Path, Path>();
    FileStatus[] categories = fs.listStatus(tmpJobOutputPath);
    for (FileStatus categoryDir : categories) {
      String categoryName = categoryDir.getPath().getName();
      Path destDir = new Path(srcCluster.getLocalDestDir(categoryName,
          commitTime));
      FileStatus[] files = fs.listStatus(categoryDir.getPath());
      for (FileStatus file : files) {
        Path destPath = new Path(destDir, file.getPath().getName());
        LOG.debug("Moving [" + file.getPath() + "] to [" + destPath + "]");
        mvPaths.put(file.getPath(), destPath);
      }
      publishMissingPaths(fs,
          srcCluster.getLocalFinalDestDirRoot(), commitTime, categoryName);
    }
    return mvPaths;
  }

  Map<Path, Path> populateTrashCommitPaths(Set<FileStatus> trashSet) {
    // find trash paths
    Map<Path, Path> trashPaths = new TreeMap<Path, Path>();
    Path trash = srcCluster.getTrashPathWithDateHour();
    Iterator<FileStatus> it = trashSet.iterator();
    while (it.hasNext()) {
      FileStatus src = it.next();
      Path target = null;
      target = new Path(trash, src.getPath().getParent().getName() + "-"
          + src.getPath().getName());
      LOG.debug("Trashing [" + src.getPath() + "] to [" + target + "]");
      trashPaths.put(src.getPath(), target);
    }
    return trashPaths;
  }

  private void commit(Map<Path, Path> commitPaths) throws Exception {
    LOG.info("Committing " + commitPaths.size() + " paths.");
    FileSystem fs = FileSystem.get(srcCluster.getHadoopConf());
    for (Map.Entry<Path, Path> entry : commitPaths.entrySet()) {
      LOG.info("Renaming " + entry.getKey() + " to " + entry.getValue());
      fs.mkdirs(entry.getValue().getParent());
      if (fs.rename(entry.getKey(), entry.getValue()) == false) {
        LOG.warn("Rename failed, aborting transaction COMMIT to avoid "
            + "dataloss. Partial data replay could happen in next run");
        throw new Exception("Abort transaction Commit. Rename failed from ["
            + entry.getKey() + "] to [" + entry.getValue() + "]");
      }
    }

  }

  private long createMRInput(Path inputPath,Map<FileStatus, String> fileListing, 
      Set<FileStatus> trashSet,Map<String, FileStatus> checkpointPaths) throws IOException {
    FileSystem fs = FileSystem.get(srcCluster.getHadoopConf());

    createListing(fs, fs.getFileStatus(srcCluster.getDataDir()), fileListing,
        trashSet, checkpointPaths);

    // if file listing is empty, simply return
    if (fileListing.isEmpty()) {
      return 0;
    }

    // the total size of data present in all files
    long totalSize = 0;
    SequenceFile.Writer out = SequenceFile.createWriter(fs, srcCluster.getHadoopConf(),
        inputPath, Text.class, FileStatus.class);
    try {
      Iterator<Entry<FileStatus, String>> it = fileListing.entrySet().iterator();
      while (it.hasNext()) {
        Entry<FileStatus, String> entry = it.next();
        FileStatus status = FileUtil.getFileStatus(entry.getKey(), buffer, in);
        out.append(new Text(entry.getValue()), status);

        // Create a sync point after each entry. This will ensure that SequenceFile
        // Reader can work at file entry level granularity, given that SequenceFile
        // Reader reads from the starting of sync point.
        out.sync();

        totalSize += entry.getKey().getLen();
      }
    } finally {
      out.close();
    }

    return totalSize;
  }

  public void createListing(FileSystem fs, FileStatus fileStatus,
      Map<FileStatus, String> results, Set<FileStatus> trashSet,
      Map<String, FileStatus> checkpointPaths) throws IOException {
    createListing(fs, fileStatus, results, trashSet, checkpointPaths, 300000);
  }

  public static class CollectorPathFilter implements PathFilter {
    public boolean accept(Path path) {
      if (path.getName().endsWith("current")
          || path.getName().equalsIgnoreCase("scribe_stats"))
        return false;
      return true;
    }
  }

  public void createListing(FileSystem fs, FileStatus fileStatus,
      Map<FileStatus, String> results, Set<FileStatus> trashSet,
      Map<String, FileStatus> checkpointPaths, long lastFileTimeout)
          throws IOException {
    List<FileStatus> streamsFileStatus = new ArrayList<FileStatus>();
    FileSystem srcFs = FileSystem.get(srcCluster.getHadoopConf());
    for (String stream : streamsToProcess) {
      streamsFileStatus.add(srcFs.getFileStatus(new Path(srcCluster
          .getDataDir(), stream)));
    }
    for (FileStatus stream : streamsFileStatus) {
      String streamName = stream.getPath().getName();
      LOG.debug("createListing working on Stream [" + streamName + "]");
      FileStatus[] collectors = fs.listStatus(stream.getPath());
      for (FileStatus collector : collectors) {
        TreeMap<String, FileStatus> collectorPaths = new TreeMap<String, FileStatus>();
        // check point for this collector
        String collectorName = collector.getPath().getName();
        String checkPointKey = streamName + collectorName;
        String checkPointValue = null;
        byte[] value = checkpointProvider.read(checkPointKey);
        if (value != null)
          checkPointValue = new String(value);
        LOG.debug("CheckPoint Key [" + checkPointKey + "] value [ "
            + checkPointValue + "]");

        FileStatus[] files = fs.listStatus(collector.getPath(),
            new CollectorPathFilter());

        if (files == null) {
          LOG.warn("No Files Found in the Collector " + collector.getPath()
              + " Skipping Directory");
          continue;
        }

        String currentFile = getCurrentFile(fs, files, lastFileTimeout);

        for (FileStatus file : files) {
          processFile(file, currentFile, checkPointValue, fs, results,
              collectorPaths);
        }
        populateTrash(collectorPaths, trashSet);
        populateCheckpointPathForCollector(checkpointPaths, collectorPaths,
            checkPointKey);
      } // all files in a collector
    }
  }

  private void processFile(FileStatus file, String currentFile,
      String checkPointValue, FileSystem fs, Map<FileStatus, String> results,
      Map<String, FileStatus> collectorPaths) throws IOException {

    String fileName = file.getPath().getName();
    if (fileName != null && !fileName.equalsIgnoreCase(currentFile)) {
      if (!isEmptyFile(file, fs)) {
        Path src = file.getPath().makeQualified(fs);
        String destDir = getCategoryJobOutTmpPath(getCategoryFromSrcPath(src))
            .toString();
        if (aboveCheckpoint(checkPointValue, fileName))
          results.put(file, destDir);
        collectorPaths.put(fileName, file);
      } else {
        LOG.info("Empty File [" + file.getPath() + "] found. " + "Deleting it");
        fs.delete(file.getPath(), false);
      }
    }
  }

  /*
   * Try reading a byte from a file to declare whether it's empty or not as
   * filesize isn't a right indicator in hadoop to say whether file has data or
   * not
   */
  private boolean isEmptyFile(FileStatus fileStatus, FileSystem fs) {
    boolean retVal = false;
    FSDataInputStream in = null;
    try {
      in = fs.open(fileStatus.getPath());
      byte[] data = new byte[1];
      // try reading 1 byte
      int bytesRead = in.read(data);
      if (bytesRead == 1) {
        // not empty file
        retVal = false;
      } else {
        // not able to read 1 bytes also then empty file
        retVal = true;
      }
    } catch (IOException e) {
      LOG.error(
          "Unable to find if file is empty or not [" + fileStatus.getPath()
          + "]", e);
    } finally {
      if (in != null) {
        try {
          in.close();
        } catch (IOException e1) {
          LOG.error("Error in closing file [" + fileStatus.getPath() + "]", e1);
        }
      }
    }
    return retVal;
  }

  private void populateCheckpointPathForCollector(
      Map<String, FileStatus> checkpointPaths,
      TreeMap<String, FileStatus> collectorPaths, String checkpointKey) {
    // Last file in sorted ascending order to be check-pointed for this
    // collector
    if (collectorPaths != null && collectorPaths.size() > 0) {
      Entry<String, FileStatus> entry = collectorPaths.lastEntry();
      checkpointPaths.put(checkpointKey, entry.getValue());
    }
  }

  private void populateTrash(Map<String, FileStatus> collectorPaths,
      Set<FileStatus> trashSet) {
    if (collectorPaths.size() <= FILES_TO_KEEP)
      return;
    else {
      // put collectorPaths.size() - FILES_TO_KEEP in trash path
      // in ascending order of creation
      Iterator<String> it = collectorPaths.keySet().iterator();
      int trashCnt = (collectorPaths.size() - FILES_TO_KEEP);
      int i = 0;
      while (it.hasNext() && i++ < trashCnt) {
        String fileName = it.next();
        trashSet.add(collectorPaths.get(fileName));
      }
    }
  }

  private boolean aboveCheckpoint(String checkPoint, String file) {
    if (checkPoint == null)
      return true;
    else if (file != null && file.compareTo(checkPoint) > 0) {
      return true;
    } else
      return false;
  }

  /*
   * @returns null: if there are no files or the most significant timestamped
   * file is 5 min back.
   */
  protected String getCurrentFile(FileSystem fs, FileStatus[] files,
      long lastFileTimeout) {
    // Proposed Algo :-> Sort files based on timestamp
    // if ((currentTimeStamp - last file's timestamp) > 5min ||
    // if there are no files)
    // then null (implying process this file as non-current file)
    // else
    // return last file as the current file
    class FileTimeStampComparator implements Comparator {
      public int compare(Object o, Object o1) {
        FileStatus file1 = (FileStatus) o;
        FileStatus file2 = (FileStatus) o1;
        long file1Time = file1.getModificationTime();
        long file2Time = file2.getModificationTime();
        if ((file1Time < file2Time))
          return -1;
        else
          return 1;
      }
    }

    if (files == null || files.length == 0)
      return null;
    TreeSet<FileStatus> sortedFiles = new TreeSet<FileStatus>(
        new FileTimeStampComparator());
    for (FileStatus file : files) {
      sortedFiles.add(file);
    }

    // get last file from set
    FileStatus lastFile = sortedFiles.last();

    long currentTime = System.currentTimeMillis();
    long lastFileTime = lastFile.getModificationTime();
    if (currentTime - lastFileTime >= lastFileTimeout) {
      return null;
    } else
      return lastFile.getPath().getName();
  }

  private String getCategoryFromSrcPath(Path src) {
    return src.getParent().getParent().getName();
  }

  private String getCategoryFromDestPath(Path dest) {
    return dest.getParent().getParent().getParent().getParent().getParent()
        .getParent().getName();
  }

  private Path getCategoryJobOutTmpPath(String category) {
    return new Path(tmpJobOutputPath, category);
  }


  protected void setBytesPerMapper(long bytesPerMapper) {
    BYTES_PER_MAPPER = bytesPerMapper;
  }

  /*
    The visiblity of method is set to protected to enable unit testing
   */
  protected Job createJob(Path inputPath, long totalSize) throws IOException {
    String jobName = getName();
    Configuration conf = currentCluster.getHadoopConf();

    Job job = new Job(conf);
    job.setJobName(jobName);
    //DistributedCache.addFileToClassPath(inputFormatJarDestPath, job.getConfiguration());
    job.getConfiguration().set("tmpjars", inputFormatJarDestPath.toString());
    LOG.debug("Adding file ["  + inputFormatJarDestPath + "] to distributed cache");
    job.setInputFormatClass(org.apache.hadoop.tools.mapred.UniformSizeInputFormat.class);

    Class<? extends Mapper> mapperClass = getMapperClass();
    job.setJarByClass(mapperClass);

    job.setMapperClass(mapperClass);
    job.setNumReduceTasks(0);

    job.setOutputFormatClass(NullOutputFormat.class);
    job.getConfiguration().set("mapred.map.tasks.speculative.execution",
        "false");
    job.getConfiguration().set(LOCALSTREAM_TMP_PATH, tmpPath.toString());
    job.getConfiguration().set(SRC_FS_DEFAULT_NAME_KEY,
        srcCluster.getHadoopConf().get(FS_DEFAULT_NAME_KEY));

    // set configurations needed for UniformSizeInputFormat
    int numMaps = getNumMapsForJob(totalSize);
    job.getConfiguration().setInt(DistCpConstants.CONF_LABEL_NUM_MAPS, numMaps);
    job.getConfiguration().setLong(
        DistCpConstants.CONF_LABEL_TOTAL_BYTES_TO_BE_COPIED, totalSize);
    job.getConfiguration().set(DistCpConstants.CONF_LABEL_LISTING_FILE_PATH,
        inputPath.toString());
    LOG.info("Expected number of maps [" + numMaps + "] Total data size [" + 
        totalSize + "]");

    return job;
  }

  private int getNumMapsForJob(long totalSize) {
    String mbPerMapper = System.getProperty(DatabusConstants.MB_PER_MAPPER);
    if (mbPerMapper != null) {
      BYTES_PER_MAPPER = Long.parseLong(mbPerMapper) * 1024 * 1024;
    }
    int numMaps = (int) Math.ceil(totalSize * 1.0 / BYTES_PER_MAPPER);
    return numMaps;
  }

  /*
   * The visiblity of method is set to protected to enable unit testing
   */
  protected Class<? extends Mapper> getMapperClass() {
    String className = srcCluster.getCopyMapperImpl();
    if (className == null || className.isEmpty()) {
      return CopyMapper.class;
    } else {
      try {
        return (Class<? extends Mapper>) Class.forName(className);
      } catch (ClassNotFoundException e) {
        throw new IllegalArgumentException("Copy mapper Impl " + className
            + "is not found in class path");
      }
    }
  }

  public Cluster getCurrentCluster() {
    return currentCluster;
  }
}
