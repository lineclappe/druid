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

import org.apache.druid.spark.SparkFunSuite
import org.apache.druid.timeline.DataSegment
import org.apache.spark.sql.catalyst.InternalRow
import org.apache.spark.sql.sources.v2.DataSourceOptions
import org.scalatest.matchers.should.Matchers

import scala.collection.JavaConverters.{asScalaBufferConverter, mapAsJavaMapConverter,
  seqAsJavaListConverter}

class DruidDataSourceReaderSuite extends SparkFunSuite with Matchers
  with DruidDataSourceV2TestUtils {

  test("DruidDataSourceReader should correctly read directly specified segments") {
    val expected = Seq(
      InternalRow.fromSeq(Seq(1577836800000L, List("dim1"), 1, 1, 2, 1, 1, 3, 4.2, 1.7)),
      InternalRow.fromSeq(Seq(1577836800000L, List("dim2"), 1, 1, 2, 1, 4, 2, 5.1, 8.9)),
      InternalRow.fromSeq(Seq(1577836800000L, List("dim2"), 2, 1, 2, 1, 1, 5, 8.0, 4.15)),
      InternalRow.fromSeq(Seq(1577836800000L, List("dim1"), 1, 1, 2, 1, 3, 1, 0.2, 0.0)),
      InternalRow.fromSeq(Seq(1577923200000L, List("dim2"), 3, 2, 1, 1, 1, 7, 0.0, 19.0)),
      InternalRow.fromSeq(Seq(1577923200000L, List("dim1", "dim3"), 2, 3, 7, 1, 2, 4, 11.17, 3.7))
    )

    val segmentsString = DruidDataSourceV2.MAPPER.writeValueAsString(
      List[DataSegment](firstSegment, secondSegment, thirdSegment).asJava
    )
    val dsoMap = Map("segments" -> segmentsString)
    val dso = new DataSourceOptions(dsoMap.asJava)
    val reader = DruidDataSourceReader(schema, dso)
    val actual =
      reader.planInputPartitions().asScala
        .flatMap(r => partitionReaderToSeq(r.createPartitionReader()))

    actual.zipAll(expected, InternalRow.empty, InternalRow.empty).forall{
      case (left, right) => compareInternalRows(left, right, schema)
    } shouldBe true
  }

  //TODO
  //test("getSegments time filter logic")
}
