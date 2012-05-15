package com.inmobi.databus;

import java.io.IOException;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.hdfs.MiniDFSCluster;
import org.apache.hadoop.mapred.JobClient;
import org.apache.hadoop.mapred.JobConf;
import org.apache.hadoop.mapred.MiniMRCluster;

public abstract class TestMiniClusterUtil {

  private MiniDFSCluster dfscluster = null;

  private MiniMRCluster mrcluster = null;

  private final Configuration CONF = new Configuration();

  // Number of datanodes in the cluster

  private static final int DEFAULT_DATANODE_COUNT = 2;
  private static final int DEFAULT_TASKTRACKER_COUNT = 1;
  private static final int DEFAULT_NUM_MR_DIRS = 1;

  public void setup(int datanodecount, int tasktrackercount, int nummrdirs)
      throws Exception {
    // Set the Test Directory as MiniClusterUtil so as to have everything in
    // common place
    String dataDir = "build/test/" + this.getClass().getName();
    System.setProperty("test.build.data", dataDir + "/data");
    System.setProperty("hadoop.log.dir", dataDir + "/test-logs");

    if (datanodecount < 0)
      datanodecount = DEFAULT_DATANODE_COUNT;

    if (tasktrackercount < 0)
      tasktrackercount = DEFAULT_TASKTRACKER_COUNT;

    if (nummrdirs <= 0)
      nummrdirs = DEFAULT_NUM_MR_DIRS;

    if ((dfscluster == null) && (datanodecount > 0)) {
      dfscluster = new MiniDFSCluster(CONF, datanodecount, true, null);
      dfscluster.waitActive();
    }

    if ((mrcluster == null) && (tasktrackercount > 0)) {
      mrcluster = new MiniMRCluster(tasktrackercount, dfscluster
          .getFileSystem().getUri().toString(), nummrdirs);
    }
  }

  public void cleanup() throws Exception {
    if (dfscluster != null) {
      // MiniDFSCluster.getBaseDir().deleteOnExit();
      dfscluster.shutdown();
    }

    if (mrcluster != null)
      mrcluster.shutdown();

    dfscluster = null;
    mrcluster = null;
  }

  public JobConf CreateJobConf() {
    if (mrcluster != null)
      return mrcluster.createJobConf();
    else
      return null;
  }

  public FileSystem GetFileSystem() throws IOException {
    if (dfscluster != null)
      return dfscluster.getFileSystem();
    else
      return null;
  }

  public void RunJob(JobConf conf) throws IOException {
    if (mrcluster != null)
      JobClient.runJob(conf);
  }

}
