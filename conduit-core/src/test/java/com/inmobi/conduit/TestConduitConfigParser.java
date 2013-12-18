package com.inmobi.conduit;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Map;

import org.testng.Assert;
import org.testng.annotations.Test;

public class TestConduitConfigParser {

  @Test
  public void testNullPath() throws Exception {
    ConduitConfigParser conduitConfigParser = new ConduitConfigParser(null);

    ConduitConfig config = conduitConfigParser.getConfig();

    Map<String, Cluster> clusterMap = config.getClusters();
    Assert.assertEquals(clusterMap.size(), 1);

    for (Map.Entry<String, Cluster> clusterentry: clusterMap.entrySet())
    {
      Cluster cluster = clusterentry.getValue();
      Assert.assertEquals(clusterentry.getKey(), "testcluster1");
      Assert.assertEquals(cluster.getName(), "testcluster1");
      Assert.assertEquals(cluster.getHdfsUrl(), "file:///");
      Assert.assertEquals(cluster.getHadoopConf().get("mapred.job.tracker"),
          "local");
      Assert.assertEquals(cluster.getJobQueueName(), "default");
      Assert.assertEquals(cluster.getRootDir(), "file://///tmp/databustest1/");
      Assert.assertEquals(cluster.getReadUrl(), cluster.getHdfsUrl());
    }

    Map<String, SourceStream> streamMap = config.getSourceStreams();
    Assert.assertEquals(streamMap.size(), 1);

    for (Map.Entry<String, SourceStream> streamEntry : streamMap.entrySet()) {
      Assert.assertEquals(streamEntry.getKey(), "test1");
      SourceStream stream = streamEntry.getValue();
      Assert.assertEquals(stream.getName(), "test1");
      Assert.assertEquals(stream.getSourceClusters().size(), 1);
      for (String clusterName : stream.getSourceClusters()) {
        Assert.assertEquals(clusterName, "testcluster1");
        Assert.assertEquals(stream.getRetentionInHours(clusterName), 24);
      }
    }
  }

  @Test
  public void testNonNullPathFromClasspath() throws Exception {
    ConduitConfigParser conduitConfigParser =
        new ConduitConfigParser("test-conduit.xml");

    ConduitConfig config = conduitConfigParser.getConfig();

    Map<String, Cluster> clusterMap = config.getClusters();
    Assert.assertEquals(clusterMap.size(), 1);

    for (Map.Entry<String, Cluster> clusterentry: clusterMap.entrySet())
    {
      Cluster cluster = clusterentry.getValue();
      Assert.assertEquals(clusterentry.getKey(), "testcluster2");
      Assert.assertEquals(cluster.getName(), "testcluster2");
      Assert.assertEquals(cluster.getHdfsUrl(), "file:///");
      Assert.assertEquals(cluster.getHadoopConf().get("mapred.job.tracker"),
          "local");
      Assert.assertEquals(cluster.getJobQueueName(), "default");
      Assert.assertEquals(cluster.getRootDir(), "file://///tmp/databustest2/");
      Assert.assertEquals(cluster.getReadUrl(), "webhdfs://");
    }

    Map<String, SourceStream> streamMap = config.getSourceStreams();
    Assert.assertEquals(streamMap.size(), 1);

    for (Map.Entry<String, SourceStream> streamEntry : streamMap.entrySet()) {
      Assert.assertEquals(streamEntry.getKey(), "test2");
      SourceStream stream = streamEntry.getValue();
      Assert.assertEquals(stream.getName(), "test2");
      Assert.assertEquals(stream.getSourceClusters().size(), 1);
      for (String clusterName : stream.getSourceClusters()) {
        Assert.assertEquals(clusterName, "testcluster2");
        Assert.assertEquals(stream.getRetentionInHours(clusterName), 48);
      }
    }
  }

  private void createTmpDatabusXml(File file) throws IOException {
    StringBuffer buffer= new StringBuffer();
    buffer.append("<databus>");
    buffer.append("<defaults>");
    buffer.append("<rootdir>/tmp/databustest3</rootdir>");
    buffer.append("<retentioninhours>96</retentioninhours>");
    buffer.append("</defaults>\n");
    buffer.append("<streams>");
    buffer.append("<stream name='test3'>");
    buffer.append("<sources>");
    buffer.append("<source>");
    buffer.append("<name>testcluster3</name>");
    buffer.append("<retentioninhours>48</retentioninhours>");
    buffer.append("</source>");
    buffer.append("<source>");
    buffer.append("<name>testcluster4</name>");
    buffer.append("</source>");
    buffer.append("</sources>");
    buffer.append("<destinations>");
    buffer.append("</destinations>");
    buffer.append("</stream>");
    buffer.append("</streams>");
    buffer.append("<clusters>");
    buffer.append("<cluster name='testcluster3' hdfsurl='file:///'");
    buffer.append(" jturl='local'");
    buffer.append(" jobqueuename='default'>");
    buffer.append("</cluster>");
    buffer.append("<cluster name='testcluster4' hdfsurl='file:///'");
    buffer.append(" jturl='localhost:8021'");
    buffer.append(" jobqueuename='databus'>");
    buffer.append("</cluster>");
    buffer.append("</clusters>");
    buffer.append("</databus>");

    BufferedWriter out = new BufferedWriter(new FileWriter(file));
    out.write(buffer.toString());
    out.close();
  }
  
  @Test
  public void testAbsolutePath() throws Exception {
    String path = "/tmp/tmp-conduit.xml";
    File file = new File(path);
    createTmpDatabusXml(file);
    ConduitConfigParser conduitConfigParser =
        new ConduitConfigParser(path);

    ConduitConfig config = conduitConfigParser.getConfig();

    Map<String, Cluster> clusterMap = config.getClusters();
    Assert.assertEquals(clusterMap.size(), 2);

    for (Map.Entry<String, Cluster> clusterentry: clusterMap.entrySet())
    {
      Cluster cluster = clusterentry.getValue();
      if (clusterentry.getKey().compareTo("testcluster3") == 0) {
        Assert.assertEquals(cluster.getName(), "testcluster3");
        Assert.assertEquals(cluster.getHdfsUrl(), "file:///");
        Assert.assertEquals(cluster.getHadoopConf().get("mapred.job.tracker"),
            "local");
        Assert.assertEquals(cluster.getJobQueueName(), "default");
        Assert
            .assertEquals(cluster.getRootDir(), "file://///tmp/databustest3/");
      }
      if (clusterentry.getKey().compareTo("testcluster4") == 0) {
        Assert.assertEquals(cluster.getName(), "testcluster4");
        Assert.assertEquals(cluster.getHdfsUrl(), "file:///");
        Assert.assertEquals(cluster.getHadoopConf().get("mapred.job.tracker"),
            "localhost:8021");
        Assert.assertEquals(cluster.getJobQueueName(), "databus");
        Assert
            .assertEquals(cluster.getRootDir(), "file://///tmp/databustest3/");
      }
    }

    Map<String, SourceStream> streamMap = config.getSourceStreams();
    Assert.assertEquals(streamMap.size(), 1);

    for (Map.Entry<String, SourceStream> streamEntry : streamMap.entrySet()) {
      Assert.assertEquals(streamEntry.getKey(), "test3");
      SourceStream stream = streamEntry.getValue();
      Assert.assertEquals(stream.getName(), "test3");
      int numSourceClusters = stream.getSourceClusters().size();
      Assert.assertEquals(numSourceClusters, 2);

      for (String clusterName : stream.getSourceClusters()) {
        if(clusterName.compareTo("testcluster3")==0) {
          Assert.assertEquals(stream.getRetentionInHours(clusterName), 48);
          numSourceClusters--;
        }
        if (clusterName.compareTo("testcluster4") == 0) {
          Assert.assertEquals(stream.getRetentionInHours(clusterName), 96);
          numSourceClusters--;
        }
      }
      Assert.assertEquals(numSourceClusters, 0);
    }
    file.delete();
  }

}
