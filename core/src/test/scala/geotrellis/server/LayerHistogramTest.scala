/*
 * Copyright 2020 Azavea
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package geotrellis.server

import cats.effect.IO

import scala.concurrent.ExecutionContext

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class LayerHistogramTest extends AnyFunSuite with Matchers {

  // This test works when the chosen sampling strategy is to work from the corners
  ignore("extents sampled from within overall extent") {
    val rt          = ResourceTile("8x8.tif")
    val samples     =
      LayerHistogram.concurrent[IO, ResourceTile](rt, 4).unsafeRunSync
    val sampleCount = samples.toOption.get.head.statistics.get.dataCells
    assert(sampleCount == 4, s"Expected 4 cells in histogram, got $sampleCount")
  }

  test(
    "histogram samples the total extent when budget is equal to the cell count"
  ) {
    val rt          = ResourceTile("8x8.tif")
    val samples     =
      LayerHistogram.concurrent[IO, ResourceTile](rt, 64).unsafeRunSync
    val sampleCount = samples.toOption.get.head.statistics.get.dataCells
    assert(
      sampleCount == 64,
      s"Expected 64 cells in histogram, got $sampleCount"
    )
  }

  test("histogram samples the total extent when budget too big") {
    val rt          = ResourceTile("8x8.tif")
    val samples     =
      LayerHistogram.concurrent[IO, ResourceTile](rt, 128).unsafeRunSync
    val sampleCount = samples.toOption.get.head.statistics.get.dataCells
    assert(
      sampleCount == 64,
      s"Expected 64 cells in histogram, got $sampleCount"
    )
  }
}
