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

import cats.effect.IO
import com.azavea.maml.ast._
import com.azavea.maml.eval._

import scala.concurrent.ExecutionContext

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class NoDataHandlingTest extends AnyFunSuite with Matchers with TileAsSourceImplicits {
  implicit val cs = cats.effect.IO.contextShift(ExecutionContext.global)

  val expr = Addition(List(RasterVar("t1"), RasterVar("t2")))
  val eval = LayerTms.curried(expr, ConcurrentInterpreter.DEFAULT[IO], None)

  test(
    "NODATA should be respected - user-defined, integer-based source celltype"
  ) {
    val t1       = IntUserDefinedNoDataArrayTile((1 to 100).toArray, 10, 10, IntUserDefinedNoDataCellType(1))
    val t2       = IntUserDefinedNoDataArrayTile((1 to 100).toArray, 10, 10, IntUserDefinedNoDataCellType(1))
    val paramMap = Map("t1" -> t1, "t2" -> t2)
    // We'll sample such that the bottom row (from 56 to 64) are excised from the result
    val res      = eval(paramMap, 0, 0, 0).unsafeRunSync
    val tileRes  = res.toOption.get.band(0)
    assert(
      tileRes.toArrayDouble.head.isNaN,
      s"Expected Double.NaN, got ${tileRes.toArrayDouble.head}"
    )
  }

  test("NODATA should be respected - different source celltypes") {
    val t1       = IntUserDefinedNoDataArrayTile((1 to 100).toArray, 10, 10, IntUserDefinedNoDataCellType(1))
    val t2       = DoubleUserDefinedNoDataArrayTile((1 to 100).map(_.toDouble).toArray, 10, 10, DoubleUserDefinedNoDataCellType(2.0))
    val paramMap = Map("t1" -> t1, "t2" -> t2)
    // We'll sample such that the bottom row (from 56 to 64) are excised from the result
    val res      = eval(paramMap, 0, 0, 0).unsafeRunSync
    val tileRes  = res.toOption.get.band(0)
    assert(
      tileRes.toArrayDouble.apply(0).isNaN,
      s"Expected Double.NaN, got ${tileRes.toArrayDouble.head}"
    )
  }
}
