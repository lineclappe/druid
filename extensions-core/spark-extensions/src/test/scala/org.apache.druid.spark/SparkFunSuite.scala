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

package org.apache.druid.spark

import java.util.UUID

import org.apache.druid.java.util.common.FileUtils
import org.apache.spark.{SparkConf, SparkContext}
import org.apache.spark.sql.SparkSession
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.BeforeAndAfterEach

import scala.reflect.io.Directory

class SparkFunSuite extends AnyFunSuite with BeforeAndAfterEach {
  // TODO: Figure out how turn off logging by package in slf4j to quiet unnecessarily verbose logs

  private val localSparkContext = new ThreadLocal[SparkContext]
  private val localSparkSession = new ThreadLocal[SparkSession]

  def sparkContext: SparkContext = localSparkContext.get()
  def sparkSession: SparkSession = localSparkSession.get()

  private def setupSparkContextAndSession(): Unit = {
    val config = Map(
      "spark.master" -> "local[*]",
      "spark.driver.allowMultipleContexts" -> "true",
      "spark.ui.enabled" -> "false",
      "spark.local.dir" -> FileUtils.createTempDir("spark-tests").getAbsolutePath,
      "spark.default.parallelism" -> "1",
      "spark.sql.shuffle.partitions" -> "1"
    )

    val sparkConf = new SparkConf(loadDefaults = true)
    sparkConf.setAppName(UUID.randomUUID.toString)
    sparkConf.setAll(config)

    localSparkContext.set(new SparkContext(sparkConf))
    localSparkSession.set(SparkSession.builder.getOrCreate())
  }

  override def beforeEach(): Unit = {
    setupSparkContextAndSession()
    super.beforeEach()
  }

  override def afterEach(): Unit = {
    super.afterEach()

    sparkContext.stop()
    Directory(sparkContext.getConf.get("spark.local.dir")).deleteRecursively()
  }
}
