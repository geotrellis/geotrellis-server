package geotrellis.store.query

import geotrellis.raster.{RasterSource, StringName}
import higherkindness.droste.{Algebra, scheme}
import jp.ne.opt.chronoscala.Imports._
import java.time.ZonedDateTime

case class RasterSourceCollection(list: List[RasterSource]) extends QueryCollection[RasterSource, List] {
  def find(query: Query): List[RasterSource] = RasterSourceCollection.eval(query)(this)
  val algebra: Algebra[QueryF, List[RasterSource]] = RasterSourceCollection.algebraList(list)
}

object RasterSourceCollection {
  import geotrellis.store.query.QueryF._

  /** Algebra that can work with List[T] */
  def algebraList[T <: RasterSource](list: List[T]): Algebra[QueryF, List[T]] = Algebra {
    case WithName(name) => list.filter {
      _.name match {
        case StringName(v) => v == name
        case _             => false
      }
    }
    case WithNames(names) => list.filter {
      _.name match {
        case StringName(v) => names.contains(v)
        case _             => false
      }
    }
    case At(t, fn)           => list.filter(_.metadata.attributes.get(fn.name).map(ZonedDateTime.parse).fold(false)(_ == t))
    case Between(t1, t2, fn) => list.filter {
      _.metadata.attributes.get(fn.name).map(ZonedDateTime.parse).fold(false) { current => t1 >= current && t2 < current }
    }
    case Intersects(e) => list.filter(_.extent.intersects(e))
    case Covers(e)     => list.filter(_.extent.covers(e))
    case Contains(e)   => list.filter(_.extent.covers(e))
    case And(e1, e2)   => e1 diff e2
    case Or(e1, e2)    => e1 ++ e2
  }

  /** An alias for [[scheme.cata]] since it can confuse people */
  def eval[T <: RasterSource](query: Query)(qc: QueryCollection[T, List]): List[T] =
    scheme.cata(qc.algebra).apply(query)
}
