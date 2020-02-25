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

package geotrellis.store.query

import geotrellis.raster.{RasterSource, StringName}
import geotrellis.vector.ProjectedExtent
import higherkindness.droste.{Algebra, scheme}
import jp.ne.opt.chronoscala.Imports._

import java.time.ZonedDateTime

case class RasterSourceCollection[T <: RasterSource](list: List[T]) extends QueryCollection[T, List] {
  def find(query: Query): List[T] = RasterSourceCollection.eval(query)(list)
}

object RasterSourceCollection {
  import geotrellis.store.query.QueryF._

  /** Algebra that can work with List[T] */
  def algebra[T <: RasterSource]: Algebra[QueryF, List[T] => List[T]] = Algebra {
    case Nothing() => _ => Nil
    case All()     => identity
    case WithName(name) => _.filter {
      _.name match {
        case StringName(v) => v == name
        case _             => false
      }
    }
    case WithNames(names) => _.filter {
      _.name match {
        case StringName(v) => names.contains(v)
        case _             => false
      }
    }
    case At(t, fn)           => _.filter(_.metadata.attributes.get(fn.name).map(ZonedDateTime.parse).fold(false)(_ == t))
    case Between(t1, t2, fn) => _.filter {
      _.metadata.attributes.get(fn.name).map(ZonedDateTime.parse).fold(false) { current => t1 >= current && t2 < current }
    }
    case Intersects(e: ProjectedExtent) => _.filter(_.projectedExtent.intersects(e))
    case Covers(e: ProjectedExtent)     => _.filter(_.projectedExtent.covers(e))
    case Contains(e: ProjectedExtent)   => _.filter(_.projectedExtent.covers(e))
    case And(e1, e2)   => list => val left = e1(list); left intersect e2(left)
    case Or(e1, e2)    => list => e1(list) ++ e2(list)
  }

  /** An alias for [[scheme.cata]] since it can confuse people */
  def eval[T <: RasterSource](query: Query)(list: List[T]): List[T] =
    scheme.cata(algebra[T]).apply(query)(list)
}
