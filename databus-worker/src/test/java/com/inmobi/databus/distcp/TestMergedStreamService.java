package com.inmobi.databus.distcp;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.testng.Assert;

import com.inmobi.databus.AbstractServiceTest;
import com.inmobi.databus.Cluster;
import com.inmobi.databus.DatabusConfig;
import com.inmobi.databus.PublishMissingPathsTest;
import com.inmobi.databus.SourceStream;

public class TestMergedStreamService extends MergedStreamService
    implements AbstractServiceTest {
  private static final Log LOG = LogFactory
      .getLog(TestMergedStreamService.class);
  
  private Cluster destinationCluster = null;
  private Cluster srcCluster = null;
  private FileSystem fs = null;
  private Map<String, List<String>> files = null;
  private Calendar behinddate = new GregorianCalendar();
  private Date todaysdate = null;
  
  public TestMergedStreamService(DatabusConfig config, Cluster srcCluster,
      Cluster destinationCluster, Cluster currentCluster) throws Exception {

    super(config, srcCluster, destinationCluster, currentCluster);
    this.srcCluster = srcCluster;
    this.destinationCluster = destinationCluster;
    this.fs = FileSystem.getLocal(new Configuration());
  }
  
  public static void getAllFiles(Path listPath, FileSystem fs, 
      List<String> fileList) 
          throws IOException {
    FileStatus[] fileStatuses = null;
    try {
      fileStatuses = fs.listStatus(listPath);
    } catch (FileNotFoundException e) {
    }
    if (fileStatuses == null || fileStatuses.length == 0) {
      LOG.debug("No files in directory:" + listPath);
    } else {
      for (FileStatus file : fileStatuses) { 
        if (file.isDir()) {
          getAllFiles(file.getPath(), fs, fileList);
        } else { 
          fileList.add(file.getPath().getName());
        }
      } 
    }
  }
  
  @Override
  protected void preExecute() throws Exception {
    try {
      // PublishMissingPathsTest.testPublishMissingPaths(this, false);
      if (files != null)
        files.clear();
      files = null;
      files = new HashMap<String, List<String>>();
      behinddate.add(Calendar.HOUR_OF_DAY, -2);
      for (Map.Entry<String, SourceStream> sstream : getConfig()
          .getSourceStreams().entrySet()) {
        
        LOG.debug("Working for Stream in Merged Stream Service "
            + sstream.getValue().getName());

        List<String> filesList = new ArrayList<String>();        
        String listPath = srcCluster.getLocalFinalDestDirRoot()
            + sstream.getValue().getName();
          
        LOG.debug("Getting List of Files from Path: " + listPath);
        getAllFiles(new Path(listPath), fs, filesList);
        files.put(sstream.getValue().getName(), filesList);
        LOG.debug("Creating Dummy commit Path for verifying Missing Paths");
        String dummycommitpath = this.destinationCluster.getFinalDestDirRoot()
            + sstream.getValue().getName() + File.separator
            + Cluster.getDateAsYYYYMMDDHHMNPath(behinddate.getTime());
        fs.mkdirs(new Path(dummycommitpath));
      }
      
    } catch (Exception e) {
      e.printStackTrace();
      throw new RuntimeException("Error in MergedStreamService Test PreExecute");
    } catch (AssertionError e) {
      e.printStackTrace();
      throw new RuntimeException("Error in MergedStreamService Test PreExecute");
    }
    todaysdate = null;
    todaysdate = new Date();
    
  }
  
  @Override
  protected void postExecute() throws Exception {
    try {
      for (String sstream : getConfig().getClusters().
          get(destinationCluster.getName()).getPrimaryDestinationStreams()) {
        if (srcCluster.getSourceStreams().contains(sstream)) {
          List<String> filesList = files.get(sstream);

          LOG.debug("Verifying Missing Paths for Merged Stream");

          if (filesList.size() > 0) {
            PublishMissingPathsTest.VerifyMissingPublishPaths(fs,
                todaysdate.getTime(), behinddate,
                this.destinationCluster.getFinalDestDirRoot()
                + sstream);

            String commitpath = destinationCluster.getFinalDestDirRoot()
                + sstream;          

            LOG.debug("Verifying Merged Paths in Stream for directory "
                + commitpath);
            List<String> commitPaths = new ArrayList<String>();
            getAllFiles(new Path(commitpath), fs, commitPaths);
            try {
              LOG.debug("Checking in Path for Merged mapred Output, No. of files: "
                  + commitPaths.size());

              for (int j = 0; j < filesList.size() - 1; ++j) {
                String checkpath = filesList.get(j);
                LOG.debug("Merged Checking file: " + checkpath);
                Assert.assertTrue(commitPaths.contains(checkpath));
              }
            } catch (NumberFormatException e) {
            }
          }
        }
      }
    } catch (Exception e) {
      e.printStackTrace();
      throw new RuntimeException(
          "Error in MergedStreamService Test PostExecute");
    } catch (AssertionError e) {
      e.printStackTrace();
      throw new RuntimeException(
          "Error in MergedStreamService Test PostExecute");
    }
  }
  
  public void runExecute() throws Exception {
    super.execute();
  }
  
  public void runPreExecute() throws Exception {
    preExecute();
  }
  
  public void runPostExecute() throws Exception {
    postExecute();
  }

  @Override
  public void publishMissingPaths(long commitTime) throws Exception {
    super.publishMissingPaths(fs, destinationCluster.getFinalDestDirRoot(), 
        commitTime);
  }
  
  @Override
  public Cluster getCluster() {
    return destinationCluster;
  }
  
  public FileSystem getFileSystem() {
    return fs;
  }

}

