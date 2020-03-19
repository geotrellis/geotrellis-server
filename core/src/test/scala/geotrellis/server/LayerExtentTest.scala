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

import geotrellis.raster._
import geotrellis.vector._
import geotrellis.raster.resample.NearestNeighbor

import org.scalatest._

import scala.concurrent.ExecutionContext

class LayerExtentTest extends FunSuite with Matchers {
  implicit val cs = cats.effect.IO.contextShift(ExecutionContext.global)

  test("ability to read a selected extent") {
    val rt = ResourceTile("8x8.tif", NearestNeighbor)
    val eval = LayerExtent.identity(rt)
    // We'll sample such that the bottom row (from 56 to 64) are excised from the result
    val sampled = eval(Extent(0, 1, 8, 8), CellSize(1, 1)).unsafeRunSync
    val sample = sampled.toOption.get.band(0).toArray()
    val sampleSum = sample.sum
    assert(sampleSum == 1596, s"Expected sum of 1596, got $sampleSum")
  }
}
