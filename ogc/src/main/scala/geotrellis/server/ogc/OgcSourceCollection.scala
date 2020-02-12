package geotrellis.server.ogc

import geotrellis.store.query._
import geotrellis.proj4._
import higherkindness.droste.{Algebra, scheme}
import jp.ne.opt.chronoscala.Imports._
import java.time.ZonedDateTime

import geotrellis.vector.Extent

case class OgcSourceCollection(list: List[OgcSource]) extends QueryCollection[OgcSource] {
  def find(query: Query): List[OgcSource] = OgcSourceCollection.eval(query)(list)

  val algebra: Algebra[QueryF, List[OgcSource]] = OgcSourceCollection.ogcSourceListAlg(list)
}

object OgcSourceCollection {
  import geotrellis.store.query.QueryF._

  def ogcSourceListAlg(list: List[OgcSource]): Algebra[QueryF, List[OgcSource]] = Algebra {
    case WithName(name)      => list.filter(_.name == name)
    case WithNames(names)    => list.filter(rs => names.contains(rs.name))
    case At(t, fn)           => list.filter(_.metadata.attributes.get(fn.name).map(ZonedDateTime.parse).fold(false)(_ == t))
    case Between(t1, t2, fn) => list.filter {
      _.metadata.attributes.get(fn.name).map(ZonedDateTime.parse).fold(false) { current => t1 >= current && t2 < current }
    }
    case Intersects(e) =>
      list.filter(_.nativeExtent.intersects(Extent(e.getEnvelopeInternal).reproject(CRS.fromEpsgCode(3857), LatLng)))
    case Covers(e)     => list.filter(_.nativeExtent.covers(e))
    case Contains(e)   => list.filter(_.nativeExtent.covers(e))
    case And(e1, e2)   => e1 diff e2
    case Or(e1, e2)    => e1 ++ e2
  }

  def eval(query: Query)(list: List[OgcSource]): List[OgcSource] =
    scheme.cata(ogcSourceListAlg(list)).apply(query)

}
