/*
 * Copyright 2014 Twitter, Inc. Licensed under the Apache License, Version 2.0 (the "License"); you
 * may not use this file except in compliance with the License. You may obtain a copy of the License
 * at http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law or agreed to in
 * writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific
 * language governing permissions and limitations under the License.
 */
package com.twitter.hraven.datasource;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

import com.google.common.base.Stopwatch;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.client.HTable;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.ResultScanner;
import org.apache.hadoop.hbase.client.Scan;
import org.apache.hadoop.hbase.filter.Filter;
import org.apache.hadoop.hbase.filter.PrefixFilter;
import org.apache.hadoop.hbase.filter.WhileMatchFilter;
import org.apache.hadoop.hbase.util.Bytes;

import com.twitter.hraven.*;

/**
 * Service that accesses the hdfs stats tables
 * and populates the HdfsStats object
 */
public class HdfsStatsService {
  private static Log LOG = LogFactory.getLog(HdfsStatsService.class);

  private final Configuration myConf;
  private final HTable hdfsUsageTable;

  private final int defaultScannerCaching;
  private final HdfsStatsKeyConverter hdfsStatsKeyConv;

  public HdfsStatsService(Configuration conf) throws IOException {
    this.myConf = conf;
    this.hdfsUsageTable = new HTable(myConf, HdfsConstants.HDFS_USAGE_TABLE_BYTES);
    this.defaultScannerCaching = myConf.getInt("hbase.client.scanner.caching", 100);
    LOG.info(" in HdfsStatsService constuctor " + Bytes.toString(hdfsUsageTable.getTableName()));
    hdfsStatsKeyConv = new HdfsStatsKeyConverter();
  }

  public static long getLastHourInvertedTimestamp(long now) {
    long lastHour = now - (now % 3600);
    return (Long.MAX_VALUE - lastHour);
  }

  /**
   * Gets hdfs stats about all dirs on the given cluster
   * @param cluster
   * @param pathPrefix
   * @param limit
   * @param runId
   * @return list of hdfs stats
   * @throws IOException
   */
  public List<HdfsStats> getAllDirs(String cluster, String pathPrefix, int limit, long runId)
      throws IOException {
    long lastHourInverted = getLastHourInvertedTimestamp(runId);
    LOG.info(" last hour ts : " + lastHourInverted);
    String rowPrefixStr = Long.toString(lastHourInverted) + HdfsConstants.SEP + cluster; 
    if(StringUtils.isNotEmpty(pathPrefix)) {
      rowPrefixStr += HdfsConstants.SEP + pathPrefix;
    }
    LOG.info(" row prefix : " + rowPrefixStr);
    byte[] rowPrefix = Bytes.toBytes(rowPrefixStr);
    Scan scan = new Scan();
    scan.setStartRow(rowPrefix);
    scan.addColumn(HdfsConstants.DISK_INFO_FAM_BYTES, HdfsConstants.FILE_COUNT_COLUMN_BYTES);
    scan.addColumn(HdfsConstants.DISK_INFO_FAM_BYTES, HdfsConstants.DIR_COUNT_COLUMN_BYTES);
    scan.addColumn(HdfsConstants.DISK_INFO_FAM_BYTES, HdfsConstants.ACCESS_COUNT_TOTAL_COLUMN_BYTES);
    scan.addColumn(HdfsConstants.DISK_INFO_FAM_BYTES, HdfsConstants.ACCESS_COST_COLUMN_BYTES);
    scan.addColumn(HdfsConstants.DISK_INFO_FAM_BYTES, HdfsConstants.STORAGE_COST_COLUMN_BYTES);
    scan.addColumn(HdfsConstants.DISK_INFO_FAM_BYTES, HdfsConstants.SPACE_CONSUMED_COLUMN_BYTES);
    scan.addColumn(HdfsConstants.DISK_INFO_FAM_BYTES, HdfsConstants.TMP_FILE_COUNT_COLUMN_BYTES);
    scan.addColumn(HdfsConstants.DISK_INFO_FAM_BYTES, HdfsConstants.TMP_SPACE_CONSUMED_COLUMN_BYTES);
    scan.addColumn(HdfsConstants.DISK_INFO_FAM_BYTES, HdfsConstants.TRASH_FILE_COUNT_COLUMN_BYTES);
    scan.addColumn(HdfsConstants.DISK_INFO_FAM_BYTES, HdfsConstants.TRASH_SPACE_CONSUMED_COLUMN_BYTES);
    scan.addColumn(HdfsConstants.DISK_INFO_FAM_BYTES, HdfsConstants.OWNER_COLUMN_BYTES);
    scan.addColumn(HdfsConstants.DISK_INFO_FAM_BYTES, HdfsConstants.QUOTA_COLUMN_BYTES);
    scan.addColumn(HdfsConstants.DISK_INFO_FAM_BYTES, HdfsConstants.SPACE_QUOTA_COLUMN_BYTES);

    // using a large scanner caching value with a small limit can mean we scan a lot more data than
    // necessary, so lower the caching for low limits
    scan.setCaching(Math.min(limit, defaultScannerCaching));
    // require that all rows match the prefix we're looking for
    Filter prefixFilter = new WhileMatchFilter(new PrefixFilter(rowPrefix));
    scan.setFilter(prefixFilter);
    return createFromResults(cluster, scan, limit);

  }

  /** 
   * Scans the hbase table and populates the hdfs stats
   * @param cluster
   * @param scan
   * @param maxCount
   * @return
   * @throws IOException
   */
  private List<HdfsStats> createFromResults(String cluster, Scan scan, int maxCount) throws IOException {
    List<HdfsStats> hdfsStats = new LinkedList<HdfsStats>();
    ResultScanner scanner = null;
    try {
      Stopwatch timer = new Stopwatch().start();
      int rowCount = 0;
      long colCount = 0;
      long resultSize = 0;
      scanner = hdfsUsageTable.getScanner(scan);
      HdfsStats currentHdfsStats = null;
      for (Result result : scanner) {
        if (result != null && !result.isEmpty()) {
          rowCount++;
          colCount += result.size();
          resultSize += result.getWritableSize();
          LOG.info( rowCount + "  row: " + Bytes.toString(result.getRow()));
          HdfsStatsKey currentKey = hdfsStatsKeyConv.fromBytes(result.getRow());
            // return if we've already hit the limit
            if (hdfsStats.size() >= maxCount) {
              break;
            }
            currentHdfsStats = new HdfsStats(new HdfsStatsKey(currentKey));
            currentHdfsStats.populate(result);
            hdfsStats.add(currentHdfsStats);
        }
      }
      timer.stop();
      LOG.info("For cluster " + cluster + " Fetched from hbase " + rowCount + " rows, "
          + colCount + " columns, " + resultSize + " bytes ( "
          + resultSize / (1024 * 1024) + ") MB, in total time of " + timer);
      } finally {
      if (scanner != null) {
        scanner.close();
      }
    }

    return hdfsStats;
  }

  public List<HdfsStats> getPathTimeSeries(String cluster, String path, int limit, long startTime) {
    // TODO Auto-generated method stub
    return null;
  }

}