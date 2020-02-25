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

package geotrellis.server.ogc

import geotrellis.store.query._
import higherkindness.droste.{Algebra, scheme}
import jp.ne.opt.chronoscala.Imports._
import java.time.ZonedDateTime

case class OgcSourceRepository(store: List[OgcSource]) extends Repository[OgcSource, List] {
  def find(query: Query): List[OgcSource] = OgcSourceRepository.eval(query)(store)
}

object OgcSourceRepository {
  import geotrellis.store.query.QueryF._

  def algebgra: Algebra[QueryF, List[OgcSource] => List[OgcSource]] = Algebra {
    case Nothing()           => _ => Nil
    case All()               => identity
    case WithName(name)      => _.filter(_.name == name)
    case WithNames(names)    => _.filter(rs => names.contains(rs.name))
    case At(t, fn)           => _.filter(_.metadata.attributes.get(fn).map(ZonedDateTime.parse).fold(false)(_ == t))
    case Between(t1, t2, fn) => _.filter {
      _.metadata.attributes.get(fn).map(ZonedDateTime.parse).fold(false) { current => t1 >= current && t2 < current }
    }
    case Intersects(e) => _.filter(_.nativeProjectedExtent.intersects(e))
    case Covers(e)     => _.filter(_.nativeProjectedExtent.covers(e))
    case Contains(e)   => _.filter(_.nativeProjectedExtent.covers(e))
    case And(e1, e2)   => list => val left = e1(list); left intersect e2(left)
    case Or(e1, e2)    => list => e1(list) ++ e2(list)
  }

  /** An alias for [[scheme.cata]] since it can confuse people */
  def eval(query: Query)(list: List[OgcSource]): List[OgcSource] =
    scheme.cata(algebgra).apply(query)(list)

}
