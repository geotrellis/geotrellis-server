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

import geotrellis.server.extent.SampleUtils
import geotrellis.vector._
import cats.data.{NonEmptyList => NEL}

import org.scalatest._

/**
  * There are various possible relationships between extents of raster, we need to have union and
  *  intersection strategies which handle each of them appropriately
  *
 * case 1: all extents share some intersection extent
  * case 2: some intersections exist among the extents but no single shared extent
  * case 3: no intersection among the extents
  *
 */
class ExtentMergeStrategyTest extends FunSuite with Matchers {

  test("total overlap - intersection") {
    val e1      = Extent(0, 0, 100, 100)
    val e2      = Extent(50, 50, 150, 150)
    val e3      = Extent(75, 75, 90, 90)
    val extents = List(e1, e2, e3)
    extents.permutations.toList.map { permutation =>
      assert(SampleUtils.intersectExtents(NEL.fromListUnsafe(permutation)) == Some(Extent(75, 75, 90, 90)))
    }
  }

  test("partial overlap - intersection") {
    val e1      = Extent(0, 0, 100, 100)
    val e2      = Extent(50, 50, 150, 150)
    val e3      = Extent(125, 125, 200, 200)
    val extents = List(e1, e2, e3)
    extents.permutations.toList.map { permutation =>
      assert(SampleUtils.intersectExtents(NEL.fromListUnsafe(permutation)) == None)
    }
  }

  test("no overlap - intersection") {
    val e1      = Extent(0, 0, 100, 100)
    val e2      = Extent(125, 125, 200, 200)
    val e3      = Extent(225, 225, 300, 300)
    val extents = List(e1, e2, e3)
    extents.permutations.toList.map { permutation =>
      assert(SampleUtils.intersectExtents(NEL.fromListUnsafe(permutation)) == None)
    }
  }

  test("total overlap - union") {
    val e1      = Extent(0, 0, 100, 100)
    val e2      = Extent(50, 50, 150, 150)
    val e3      = Extent(75, 75, 90, 90)
    val extents = List(e1, e2, e3)
    extents.permutations.toList.map { permutation =>
      assert((extents map { SampleUtils.unionExtents(NEL.fromListUnsafe(permutation)).get contains _ }) reduce {
        _ && _
      })
    }
  }
  test("partial overlap - union") {
    val e1      = Extent(0, 0, 100, 100)
    val e2      = Extent(50, 50, 150, 150)
    val e3      = Extent(125, 125, 200, 200)
    val extents = List(e1, e2, e3)
    extents.permutations.toList.map { permutation =>
      assert((extents map { SampleUtils.unionExtents(NEL.fromListUnsafe(permutation)).get contains _ }) reduce {
        _ && _
      })
    }
  }
  test("no overlap - union") {
    val e1      = Extent(0, 0, 100, 100)
    val e2      = Extent(125, 125, 200, 200)
    val e3      = Extent(225, 225, 300, 300)
    val extents = List(e1, e2, e3)
    extents.permutations.toList.map { permutation =>
      assert((extents map { SampleUtils.unionExtents(NEL.fromListUnsafe(permutation)).get contains _ }) reduce {
        _ && _
      })
    }
  }
}
