package geotrellis.server.ogc

import geotrellis.store.query._

import higherkindness.droste.{Algebra, scheme}
import jp.ne.opt.chronoscala.Imports._
import java.time.ZonedDateTime

case class OgcSourceCollection(list: List[OgcSource]) extends QueryCollection[OgcSource, List] {
  def find(query: Query): List[OgcSource] = OgcSourceCollection.eval(query)(list)
}

object OgcSourceCollection {
  import geotrellis.store.query.QueryF._

  def algebgra: Algebra[QueryF, List[OgcSource] => List[OgcSource]] = Algebra {
    case WithName(name)      => _.filter(_.name == name)
    case WithNames(names)    => _.filter(rs => names.contains(rs.name))
    case At(t, fn)           => _.filter(_.metadata.attributes.get(fn.name).map(ZonedDateTime.parse).fold(false)(_ == t))
    case Between(t1, t2, fn) => _.filter {
      _.metadata.attributes.get(fn.name).map(ZonedDateTime.parse).fold(false) { current => t1 >= current && t2 < current }
    }
    case Intersects(e) => _.filter(_.nativeExtent.intersects(e))
    case Covers(e)     => _.filter(_.nativeExtent.covers(e))
    case Contains(e)   => _.filter(_.nativeExtent.covers(e))
    case And(e1, e2)   => list => val left = e1(list); left intersect e2(left)
    case Or(e1, e2)    => list => e1(list) ++ e2(list)
  }

  /** An alias for [[scheme.cata]] since it can confuse people */
  def eval(query: Query)(list: List[OgcSource]): List[OgcSource] =
    scheme.cata(algebgra).apply(query)(list)

}
