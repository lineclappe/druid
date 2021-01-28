/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.druid.spark.utils

object DruidDataSourceOptionKeys {
  // Metadata Client Configs
  val metadataDbTypeKey: String = "metadataDbType"
  val metadataHostKey: String = "metadataHost" // Default: localhost
  val metadataPortKey: String = "metadataPort"
  val metadataConnectUriKey: String = "metadataConnectUri"
  val metadataUserKey: String = "metadataUser"
  val metadataPasswordKey: String = "metadataPassword"
  val metadataDbcpPropertiesKey: String = "metadataDbcpProperties"
  val metadataBaseNameKey: String = "metadataBaseName" // Default: druid

  // Druid Client Configs
  val brokerHostKey: String = "brokerHost" // Default: localhost
  val brokerPortKey: String = "brokerPort" // Default: 8082

  // Reader Configs
  val segmentsKey: String = "segments"
  val useCompactSketchesKey: String = "useCompactSketches" // Default: false

  // Writer Configs
  val versionKey: String = "version"
  val dimensionsKey: String = "dimensions"
  val metricsKey: String = "metrics"
  val excludedDimensionsKey: String = "excludedDimensions"
  val segmentGranularity: String = "segmentGranularity" // Default: All
  val queryGranularity: String = "queryGranularity" // Default: None
  val partitionsMapKey: String = "partitionMap"
  val deepStorageTypeKey: String = "deepStorageType" // Default: local
  val timestampColumnKey: String = "timestampColumn" // Default: ts
  val timestampFormatKey: String = "timestampFormat" // Default: auto
  val shardSpecTypeKey: String = "shardSpecType" // Default: linear
  val rollUpSegmentsKey: String = "rollUpSegments" // Default: true
  val rowsPerPersistKey: String = "rowsPerPersist" // Default: 2000000

  // Local SegmentWriter Configs
  val localDeepStorageTypeKey: String = "local"
  val localStorageDirectoryKey: String = "storageDirectory"

  // HDFS SegmentWriter Configs
  val hdfsDeepStorageTypeKey: String = "hdfs"
  val hdfsHadoopConfKey: String = "hadoopConf"
  val hdfsPusherConfigKey: String = "hdfsPusherConfig"

  // S3 SegmentWriter Configs
  val s3DeepStorageTypeKey: String = "s3"
  val s3ServerSideEncryptionConfigKey: String = "s3ServerSideEncryptionConfig"
  val s3DataSegmentPusherConfigKey: String = "s3DataSegmentPusherConfig"
  val s3InputDataConfigKey: String = "s3InputDataConfig"

  // Google SegmentWriter Configs
  val googleDeepStorageTypeKey: String = "google"
  val googleStorageConfigKey: String = "googleStorageConfig"
  val googleAccountConfigKey: String = "googleAccountConfig"
  val googleInputDataConfigKey: String = "googleInputDataConfig"

  // Azure SegmentWriter Configs
  val azureDeepStorageKey: String = "azure"
  val azureStorageConfigKey: String = "azureStorageConfig"
  val azureAccountConfigKey: String = "azureAccountConfig"
  val azureDataSegmentConfigKey: String = "azureDataSegmentConfig"
  val azureInputDataConfigKey: String = "azureInputDataConfig"
  val azureCloudBlobIterableFactoryConfigKey: String = "azureCloudBlobIterableFactoryConfig"
}
