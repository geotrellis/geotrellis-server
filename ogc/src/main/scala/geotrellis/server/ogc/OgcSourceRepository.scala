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
import io.circe.Json
import jp.ne.opt.chronoscala.Imports._

case class OgcSourceRepository(override val store: List[OgcSource]) extends Repository[List, OgcSource] {
  def find(query: Query): List[OgcSource] = OgcSourceRepository.eval(query)(store)
}

object OgcSourceRepository {
  import geotrellis.store.query.QueryF._

  def algebra: Algebra[QueryF, List[OgcSource] => List[OgcSource]] = Algebra {
    case Nothing()           => _ => Nil
    case All()               => identity
    case WithName(name)      => _.filter(_.name == name)
    case WithNames(names)    => _.filter(rs => names.contains(rs.name))
    case At(t, _)           => _.filter(_.timeInterval match {
      case Some(OgcTimeInterval(start, None, _)) => start == t
      case Some(OgcTimeInterval(start, Some(end), _)) => start <= t && t <= end
      // Assume valid OgcLayers with no explicit temporal component are valid
      // at all times so they aren't excluded when optional TIME param is null
      case None => true
    })
    case Between(t1, t2, _) => _.filter { _.timeInterval match {
        case Some(OgcTimeInterval(start, None, _)) => t1 <= start && start <= t2
        case Some(OgcTimeInterval(start, Some(end), _)) =>
          t1 <= start && start <= t2 || t1 <= end && end <= t2
        // Assume valid OgcLayers with no explicit temporal component are valid
        // at all times so they aren't excluded when optional TIME param is null
        case _ => true
      }
    }
    case Intersects(e) => _.filter(_.nativeProjectedExtent.intersects(e))
    case Covers(e)     => _.filter(_.nativeProjectedExtent.covers(e))
    case Contains(e)   => _.filter(_.nativeProjectedExtent.covers(e))
    case And(e1, e2)   => list => val left = e1(list); left intersect e2(left)
    case Or(e1, e2)    => list => e1(list) ++ e2(list)
  }

  /** An alias for [[scheme.cata]] since it can confuse people */
  def eval(query: Query)(list: List[OgcSource]): List[OgcSource] =
    scheme.cata(algebra).apply(query)(list)

  /** An alias for [[scheme.hylo]] since it can confuse people */
  def eval(json: Json)(list: List[OgcSource]): List[OgcSource] =
    scheme.hylo(algebra, QueryF.coalgebraJson).apply(json)(list)

}
