/*
Copyright 2014 Twitter, Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/
package com.twitter.hraven;

import java.util.NavigableMap;

import org.apache.hadoop.hbase.client.Result;
import org.codehaus.jackson.map.annotate.JsonSerialize;

/**
 * An HdfsStats object represents information about a particular
 * path on hdfs including storage, access and cost
 */
@JsonSerialize(include = JsonSerialize.Inclusion.NON_NULL)
public class HdfsStats {

  private HdfsStatsKey hdfsStatsKey;
  private long fileCount;
  private long dirCount;
  private String owner;
  private long spaceConsumed;
  private long quota;
  private long spaceQuota;
  private long tmpFileCount;
  private long tmpSpaceConsumed;
  private long trashFileCount;
  private long trashSpaceConsumed;
  private long accessCountTotal;
  private long hdfsCost;
  private long accessCost;
  private long storageCost;

  public HdfsStats() {
  }

  public HdfsStats(HdfsStatsKey hdfsStatsKey) {
    this.hdfsStatsKey = hdfsStatsKey;
  }

  public HdfsStatsKey getHdfsStatsKey() {
    return hdfsStatsKey;
  }

  public void setHdfsStatsKey(HdfsStatsKey hdfsStatsKey) {
    this.hdfsStatsKey = hdfsStatsKey;
  }

  public long getFileCount() {
    return fileCount;
  }

  public void setFileCount(long fileCount) {
    this.fileCount = fileCount;
  }

  public long getDirCount() {
    return dirCount;
  }

  public void setDirCount(long dirCount) {
    this.dirCount = dirCount;
  }

  public String getOwner() {
    return owner;
  }

  public void setOwner(String owner) {
    this.owner = owner;
  }

  public long getSpaceConsumed() {
    return spaceConsumed;
  }

  public void setSpaceConsumed(long spaceConsumed) {
    this.spaceConsumed = spaceConsumed;
  }

  public long getQuota() {
    return quota;
  }

  public void setQuota(long quota) {
    this.quota = quota;
  }

  public long getSpaceQuota() {
    return spaceQuota;
  }

  public void setSpaceQuota(long spaceQuota) {
    this.spaceQuota = spaceQuota;
  }

  public long getTmpFileCount() {
    return tmpFileCount;
  }

  public void setTmpFileCount(long tmpFileCount) {
    this.tmpFileCount = tmpFileCount;
  }

  public long getTmpSpaceConsumed() {
    return tmpSpaceConsumed;
  }

  public void setTmpSpaceConsumed(long tmpSpaceConsumed) {
    this.tmpSpaceConsumed = tmpSpaceConsumed;
  }

  public long getTrashFileCount() {
    return trashFileCount;
  }

  public void setTrashFileCount(long trashFileCount) {
    this.trashFileCount = trashFileCount;
  }

  public long getTrashSpaceConsumed() {
    return trashSpaceConsumed;
  }

  public void setTrashSpaceConsumed(long trashSpaceConsumed) {
    this.trashSpaceConsumed = trashSpaceConsumed;
  }

  public long getAccessCountTotal() {
    return accessCountTotal;
  }

  public void setAccessCountTotal(long accessCountTotal) {
    this.accessCountTotal = accessCountTotal;
  }

  public long getHdfsCost() {
    return hdfsCost;
  }

  public void setHdfsCost(long cost) {
    this.hdfsCost = cost;
  }

  /**
   * hdfs cost is the sum of access Cost and Storage Cost
   * @return hdfs cost as long
   */
  public long calculateHDFSCost(){
    return accessCost + storageCost;
  }

  public long getAccessCost() {
    return accessCost;
  }

  public void setAccessCost(long accessCost) {
    this.accessCost = accessCost;
  }

  public long getStorageCost() {
    return storageCost;
  }

  public void setStorageCost(long storageCost) {
    this.storageCost = storageCost;
  }

  /**
   * populates the hdfs stats by looking through the hbase result
   * @param result
   */
  public void populate(Result result) {
    // process path-level stats and properties
    NavigableMap<byte[], byte[]> infoValues =
        result.getFamilyMap(HdfsConstants.DISK_INFO_FAM_BYTES);

    this.fileCount = JobDetails.getValueAsLong(HdfsConstants.FILE_COUNT_COLUMN_BYTES, infoValues);
    this.dirCount = JobDetails.getValueAsLong(HdfsConstants.DIR_COUNT_COLUMN_BYTES, infoValues);
    this.spaceConsumed = JobDetails.getValueAsLong(HdfsConstants.SPACE_CONSUMED_COLUMN_BYTES,
          infoValues);
    this.accessCountTotal = JobDetails.getValueAsLong(HdfsConstants.ACCESS_COUNT_TOTAL_COLUMN_BYTES,
      infoValues);
    this.owner = JobDetails.getValueAsString(HdfsConstants.OWNER_COLUMN_BYTES, infoValues);
    this.quota = JobDetails.getValueAsLong(HdfsConstants.QUOTA_COLUMN_BYTES, infoValues);
    this.spaceQuota = JobDetails.getValueAsLong(HdfsConstants.SPACE_QUOTA_COLUMN_BYTES, infoValues);
    this.tmpFileCount = JobDetails.getValueAsLong(HdfsConstants.TMP_FILE_COUNT_COLUMN_BYTES, infoValues);
    this.tmpSpaceConsumed = JobDetails.getValueAsLong(HdfsConstants.TMP_SPACE_CONSUMED_COLUMN_BYTES, infoValues);
    this.trashFileCount = JobDetails.getValueAsLong(HdfsConstants.TRASH_FILE_COUNT_COLUMN_BYTES, infoValues);
    this.trashSpaceConsumed = JobDetails.getValueAsLong(HdfsConstants.TRASH_SPACE_CONSUMED_COLUMN_BYTES, infoValues);
    this.accessCost = JobDetails.getValueAsLong(HdfsConstants.ACCESS_COST_COLUMN_BYTES, infoValues);
    this.storageCost = JobDetails.getValueAsLong(HdfsConstants.STORAGE_COST_COLUMN_BYTES, infoValues);

  }

}