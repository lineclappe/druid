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

package org.apache.druid.spark.v2

import java.io.ByteArrayInputStream
import java.util
import java.util.{Properties, List => JList}

import com.fasterxml.jackson.core.`type`.TypeReference
import org.apache.druid.java.util.common.{DateTimes, StringUtils}
import org.apache.druid.spark.utils.DruidMetadataClient
import org.apache.druid.timeline.DataSegment
import org.apache.spark.sql.catalyst.InternalRow
import org.apache.spark.sql.sources.{And, EqualNullSafe, EqualTo, Filter, GreaterThan,
  GreaterThanOrEqual, In, IsNotNull, IsNull, LessThan, LessThanOrEqual, Not, Or, StringContains,
  StringEndsWith, StringStartsWith}
import org.apache.spark.sql.sources.v2.DataSourceOptions
import org.apache.spark.sql.sources.v2.reader.{DataSourceReader, InputPartition,
  SupportsPushDownFilters, SupportsPushDownRequiredColumns}
import org.apache.spark.sql.types.StructType

import scala.collection.JavaConverters.{asScalaBufferConverter, seqAsJavaListConverter}

/**
  * //
  * //
  *
  * To aid comprehensibility, some idiomatic Scala has been somewhat java-fied.
  *
  *
  * @param schema
  */
class DruidDataSourceReader(
                             var schema: Option[StructType] = None,
                             dataSourceOptions: DataSourceOptions
                           ) extends DataSourceReader
  with SupportsPushDownRequiredColumns with SupportsPushDownFilters {
  private lazy val metadataClient =
    DruidDataSourceReader.createDruidMetaDataClient(dataSourceOptions)
  private var filters: Array[Filter] = Array.empty

  override def readSchema(): StructType = {
    if (schema.isDefined) {
      schema.get
    } else {
      DruidDataSourceReader.getSchema(dataSourceOptions)
    }
  }

  override def planInputPartitions(): util.List[InputPartition[InternalRow]] = {
    // For now, one partition for each Druid segment partition
    // Future improvements can use information from SegmentAnalyzer results to do smart things
    if (schema.isEmpty) {
      // TODO: Figure out how readSchema is called; can we just always set schema in that method?
      schema = Option(readSchema())
    }
    // TODO: Based on schema, determine if we need to initialize executors with SketchModule.registerSerde()
    // Allow passing hard-coded list of segments to load
    if (dataSourceOptions.get("segments").isPresent) {
      val segments: JList[DataSegment] = DruidDataSourceV2.MAPPER.readValue(
        dataSourceOptions.get("segments").get(),
        new TypeReference[JList[DataSegment]]() {})
      segments.asScala
        .map(new DruidInputPartition(_, schema.get, filters): InputPartition[InternalRow]).asJava
    } else {
      getSegments
        .map(new DruidInputPartition(_, schema.get, filters): InputPartition[InternalRow]).asJava
    }
  }

  override def pruneColumns(structType: StructType): Unit = {
    schema = Option(structType)
  }

  override def pushFilters(filters: Array[Filter]): Array[Filter] = {
    filters.partition(isSupportedFilter) match {
      case (supported, unsupported) =>
        this.filters = supported
        unsupported
    }
  }

  override def pushedFilters(): Array[Filter] = filters

  private def isSupportedFilter(filter: Filter) = filter match {
    case _: And => true
    case _: Or => true
    case _: Not => true
    case _: IsNull => false // Setting null-related filters to false for now
    case _: IsNotNull => false // Setting null-related filters to false for now
    case _: In => true
    case _: StringContains => true
    case _: StringStartsWith => true
    case _: StringEndsWith => true
    case _: EqualTo => true
    case _: EqualNullSafe => false // Setting null-related filters to false for now
    case _: LessThan => true
    case _: LessThanOrEqual => true
    case _: GreaterThan => true
    case _: GreaterThanOrEqual => true

    case _ => false
  }

  private[v2] def getSegments: Seq[DataSegment] = {
    assert(dataSourceOptions.tableName().isPresent,
      s"Must set ${DataSourceOptions.TABLE_KEY}!")

    // Check filters for any bounds on __time
    // Otherwise, need to full scan
    val timeFilters = filters
      .filter(_.references.contains("__time"))
      .flatMap(DruidDataSourceReader.decomposeTimeFilters)
      .partition(_._1 == DruidDataSourceReader.LOWER)

    metadataClient.getSegmentPayloads(dataSourceOptions.tableName().get(),
      DateTimes.utc(timeFilters._1.map(_._2).max * 1000).toString("yyyy-MM-ddTHH:mm:ss.SSS'Z'"),
      DateTimes.utc(timeFilters._2.map(_._2).min * 1000).toString("yyyy-MM-ddTHH:mm:ss.SSS'Z'")
    )
  }
}

object DruidDataSourceReader {
  def apply(schema: StructType, dataSourceOptions: DataSourceOptions): DruidDataSourceReader = {
    new DruidDataSourceReader(Option(schema), dataSourceOptions)
  }

  def apply(dataSourceOptions: DataSourceOptions): DruidDataSourceReader = {
    new DruidDataSourceReader(None, dataSourceOptions)
  }

  val metadataDbTypeKey: String = "metadataDbType"
  val metadataHostKey: String = "metadataHost"
  val metadataPortKey: String = "metadataPort"
  val metadataConnectUriKey: String = "metadataConnectUri"
  val metadataUserKey: String = "metadataUser"
  val metadataPasswordKey: String = "metadataPassword"
  val metadataDbcpPropertiesKey: String = "metadataDbcpProperties"
  val metadataBaseNameKey: String = "metadataBaseName"

  /* Unfortunately, there's no single method of interacting with a Druid cluster that provides all
   * three operations we need: get segment locations, get dataSource schemata, and publish segments.
   *
   * Segment locations can be determined either via direct interaction with the metadata server or
   * via the coordinator API, but not via querying the `sys.segments` table served from a cluster
   * since the `sys.segments` table prunes load specs.
   *
   * Data source schemata can be determined via querying the `INFORMATION_SCHEMA.COLUMNS` table, via
   * SegmentMetadataQueries, or via pulling segments into memory and analyzing them. However,
   * SegmentMetadataQueries can be expensive and time-consuming for large numbers of segments. This
   * could be worked around by only checking the first and last segments for an interval, which
   * would catch schema evolution that spans the interval to query, but not schema evolution within
   * the interval and would prevent determining accurate statistics. Likewise, pulling segments into
   * memory on the driver to check their schema is expensive and inefficient and has the same schema
   * evolution and accurate statistics problem. The downside of querying the
   * `INFORMATION_SCHEMA.COLUMNS` table is that unlike sending a SegmentMetadataQuery or pulling a
   * segment into memory, we wouldn't have access to possibly useful statistics about the segments
   * that could be used to perform more efficient reading, and the Druid cluster to read from would
   * need to have sql querying initialized and be running a version of Druid >= 0.14. Since we're
   * not currently doing any intelligent partitioning for reads, concerns about statistics are
   * mostly irrelevant.
   *
   * Publishing segments can only be done via direct interaction with the metadata server.
   *
   * Since there's no way to satisfy these constraints with a single method of interaction, we will
   * need to use a metadata client and a druid client. The metadata client can fetch segment
   * locations and publish segments, and the druid client will issue sql queries to determine
   * datasource schemata. If there's concerns around performance issues due to "dumb" readers or
   * a need to support non-sql enabled Druid clusters, the druid client can instead be used to send
   * SegmentMetadataQueries. In order to allow growth in this direction and avoid requiring users
   * to include avatica jars in their Spark cluster, this client uses HTTP requests instead of the
   * JDBC protocol.
   */

  def createDruidMetaDataClient(dataSourceOptions: DataSourceOptions): DruidMetadataClient = {
    assert(dataSourceOptions.get(metadataDbTypeKey).isPresent,
      s"Must set $metadataDbTypeKey or provide segments directly!")
    val dbcpProperties = new Properties()
    if (dataSourceOptions.get(metadataDbcpPropertiesKey).isPresent) {
      // Assuming that .store was used to serialize the original DbcpPropertiesMap to a string
      dbcpProperties.load(
        new ByteArrayInputStream(
          StringUtils.toUtf8(dataSourceOptions.get(metadataDbcpPropertiesKey).get())
        )
      )
    }
    new DruidMetadataClient(
      dataSourceOptions.get(metadataDbTypeKey).get(),
      dataSourceOptions.get(metadataHostKey).orElse(""),
      dataSourceOptions.getInt(metadataPortKey, -1),
      dataSourceOptions.get(metadataConnectUriKey).orElse(""),
      dataSourceOptions.get(metadataUserKey).orElse(""),
      dataSourceOptions.get(metadataPasswordKey).orElse(""),
      dbcpProperties,
      dataSourceOptions.get(metadataBaseNameKey).orElse("druid")
    )
  }

  // TODO: Move these to DruidClient
  def getSchema(dataSourceOptions: DataSourceOptions): StructType = {
    // Read props from dataSourceOptions (e.g. broker or router host and port, etc.)
    null
  }

  private val emptyBoundSeq = Seq.empty[(Bound, Long)]

  private[v2] def decomposeTimeFilters(filter: Filter): Seq[(Bound, Long)] = {
    filter match {
      case And(left, right) =>
        Seq(left, right).filter(_.references.contains("__time")).flatMap(decomposeTimeFilters)
      case Or(left, right) => // TODO: Support
        emptyBoundSeq
      case Not(condition) => // TODO: Support
        emptyBoundSeq
      case EqualTo(field, value) =>
        if (field == "__time") {
          Seq(
            (LOWER, value.asInstanceOf[Long]),
            (UPPER, value.asInstanceOf[Long])
          )
        } else {
          emptyBoundSeq
        }
      case LessThan(field, value) =>
        if (field == "__time") {
          Seq((UPPER, value.asInstanceOf[Long] - 1))
        } else {
          emptyBoundSeq
        }
      case LessThanOrEqual(field, value) =>
        if (field == "__time") {
          Seq((UPPER, value.asInstanceOf[Long]))
        } else {
          emptyBoundSeq
        }
      case GreaterThan(field, value) =>
        if (field == "__time") {
          Seq((LOWER, value.asInstanceOf[Long] + 1))
        } else {
          emptyBoundSeq
        }
      case GreaterThanOrEqual(field, value) =>
        if (field == "__time") {
          Seq((LOWER, value.asInstanceOf[Long]))
        } else {
          emptyBoundSeq
        }
      case _ => emptyBoundSeq
    }
  }

  private[v2] sealed trait Bound
  case object LOWER extends Bound
  case object UPPER extends Bound
}
