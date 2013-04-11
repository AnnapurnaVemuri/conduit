package com.inmobi.databus;

import java.io.File;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.log4j.Logger;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.inmobi.databus.local.TestLocalStreamService;

public class PublishMissingPathsTest {
  
  private static Logger LOG = Logger.getLogger(PublishMissingPathsTest.class);

  public static void testPublishMissingPaths(AbstractServiceTest service,
      boolean local)
      throws Exception {

    FileSystem fs = FileSystem.getLocal(new Configuration());
    Calendar behinddate = new GregorianCalendar();
    Calendar todaysdate = new GregorianCalendar();

    String basePath = null;
    if (local)
      basePath = service.getCluster().getLocalFinalDestDirRoot();
    else
      basePath = service.getCluster().getFinalDestDirRoot();
    
    behinddate
        .setTimeInMillis(behinddate.getTimeInMillis() - (3600 * 2 * 1000));
    behinddate.set(Calendar.SECOND, 0);
    
    LOG.debug("Difference between times streams_publish: "
        + String.valueOf(todaysdate.getTimeInMillis()
            - behinddate.getTimeInMillis()));
    String basepublishPaths = basePath + "streams_publish" + File.separator;
    String publishPaths = basepublishPaths
        + Cluster.getDateAsYYYYMMDDHHMNPath(behinddate.getTime());
    
    LOG.debug("Create Missing Directory for streams_publish: " + publishPaths);

    fs.mkdirs(new Path(publishPaths));
    long commitTime = service.getCluster().getCommitTime();
    service.publishMissingPaths(commitTime);
    
    VerifyMissingPublishPaths(fs, todaysdate.getTimeInMillis(), behinddate,
        basepublishPaths);
    
    /*
     * todaysdate.add(Calendar.HOUR_OF_DAY, 2);
     * 
     * service.publishMissingPaths();
     * 
     * VerifyMissingPublishPaths(fs, todaysdate.getTimeInMillis(), behinddate,
     * basepublishPaths);
     */
    
    fs.delete(new Path(basepublishPaths), true);
  }
  
  public static void VerifyMissingPublishPaths(FileSystem fs, long todaysdate,
      Calendar behinddate, String basepublishPaths) throws Exception {
    long diff = todaysdate - behinddate.getTimeInMillis();
    while (diff > 180000) {
      String checkcommitpath = basepublishPaths + File.separator
          + Cluster.getDateAsYYYYMMDDHHMNPath(behinddate.getTime());
      LOG.debug("Checking for Created Missing Path: " + checkcommitpath);
      Assert.assertTrue(fs.exists(new Path(checkcommitpath)));
      behinddate.add(Calendar.MINUTE, 1);
      diff = todaysdate - behinddate.getTimeInMillis();
    }
  }
  
  @Test
  public void testPublishMissingPaths() throws Exception {
    DatabusConfigParser configParser = new DatabusConfigParser(
        "test-lss-pub-databus.xml");
    List<String> streamsToProcess = new ArrayList();
    DatabusConfig config = configParser.getConfig();
    streamsToProcess.addAll(config.getSourceStreams().keySet());
    FileSystem fs = FileSystem.getLocal(new Configuration());
    
    ArrayList<Cluster> clusterList = new ArrayList<Cluster>(config
        .getClusters().values());
    Cluster cluster = clusterList.get(0);
    TestLocalStreamService service = new TestLocalStreamService(config,
        cluster, null, new FSCheckpointProvider(cluster.getCheckpointDir()),
        streamsToProcess);
    
    ArrayList<SourceStream> sstreamList = new ArrayList<SourceStream>(config
        .getSourceStreams().values());
    
    SourceStream sstream = sstreamList.get(0);
    
    Calendar behinddate = new GregorianCalendar();

    behinddate.add(Calendar.HOUR_OF_DAY, -2);
    behinddate.set(Calendar.SECOND, 0);
    
    String basepublishPaths = cluster.getLocalFinalDestDirRoot()
        + sstream.getName() + File.separator;
    String publishPaths = basepublishPaths
        + Cluster.getDateAsYYYYMMDDHHMNPath(behinddate.getTime());
    
    fs.mkdirs(new Path(publishPaths));
    {
      Calendar todaysdate = new GregorianCalendar();
      long commitTime = cluster.getCommitTime();
      Map<String, Set<Path>> missingDirCommittedPaths = new HashMap<String,
          Set<Path>>();
      
      service.publishMissingPaths(fs, missingDirCommittedPaths, commitTime);
      service.commitPublishMissingPaths(fs, missingDirCommittedPaths, commitTime);
      
      VerifyMissingPublishPaths(fs, todaysdate.getTimeInMillis(), behinddate,
          basepublishPaths);
    }

    {
      Calendar todaysdate = new GregorianCalendar();
      long commitTime = cluster.getCommitTime();
      Map<String, Set<Path>> missingDirCommittedPaths = new HashMap<String,
          Set<Path>>();
      service.publishMissingPaths(fs, missingDirCommittedPaths, commitTime);
      service.commitPublishMissingPaths(fs, missingDirCommittedPaths, commitTime);

      VerifyMissingPublishPaths(fs, todaysdate.getTimeInMillis(), behinddate,
          basepublishPaths);
    }
    
    fs.delete(new Path(cluster.getRootDir()), true);
    
    fs.close();
  }
}
