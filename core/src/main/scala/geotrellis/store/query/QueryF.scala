package geotrellis.store.query

import geotrellis.raster.{RasterSource, StringName}
import geotrellis.vector.Geometry
import cats.Functor
import higherkindness.droste.data.Fix
import higherkindness.droste.{Algebra, scheme}
import jp.ne.opt.chronoscala.Imports._
import java.time.ZonedDateTime

trait QueryF[A]

object QueryF {
  /** Tree leaves */
  case class Or[A](l: A, r: A) extends QueryF[A]
  case class And[A](l: A, r: A) extends QueryF[A]
  case class Intersects[A](g: Geometry) extends QueryF[A]
  case class Contains[A](g: Geometry) extends QueryF[A]
  case class Covers[A](g: Geometry) extends QueryF[A]
  case class At[A](t: ZonedDateTime, fieldName: Symbol = 'time) extends QueryF[A]
  case class Between[A](from: ZonedDateTime, to: ZonedDateTime, fieldName: Symbol = 'time) extends QueryF[A]
  case class WithName[A](name: String) extends QueryF[A]
  case class WithNames[A](names: Set[String]) extends QueryF[A]

  /** Build Tree syntax */
  def or(l: Query, r: Query): Query        = Fix(Or(l, r))
  def and(l: Query, r: Query): Query       = Fix(And(l, r))
  def withName(name: String): Query        = Fix[QueryF](WithName(name))
  def withNames(names: Set[String]): Query = Fix[QueryF](WithNames(names))
  def intersects(g: Geometry): Query       = Fix[QueryF](Intersects(g))
  def contains(g: Geometry): Query         = Fix[QueryF](Contains(g))
  def covers(g: Geometry): Query           = Fix[QueryF](Covers(g))
  def at(t: ZonedDateTime, fieldName: Symbol = 'time): Query                          = Fix[QueryF](At(t, fieldName))
  def between(t1: ZonedDateTime, t2: ZonedDateTime, fieldName: Symbol = 'time): Query = Fix[QueryF](Between(t1, t2, fieldName))

  /** Pattern functor for QueryF */
  implicit val queryFFunctor: Functor[QueryF] = new Functor[QueryF] {
    def map[A, B](fa: QueryF[A])(f: A => B): QueryF[B] = fa match {
      case And(l, r)           => And(f(l), f(r))
      case Or(l, r)            => Or(f(l), f(r))
      case At(v, fn)           => At[B](v, fn)
      case Between(t1, t2, fn) => Between[B](t1, t2, fn)
      case Intersects(v)       => Intersects[B](v)
      case Contains(v)         => Contains[B](v)
      case Covers(v)           => Covers[B](v)
      case WithName(v)         => WithName[B](v)
      case WithNames(v)        => WithNames[B](v)
    }
  }

  /** Algebra that can work with List[T] */
  def rasterSourcesListAlg[T <: RasterSource](list: List[T]): Algebra[QueryF, List[T]] = Algebra {
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
  def eval[T <: RasterSource](query: Query)(list: List[T]): List[T] =
    scheme.cata(rasterSourcesListAlg(list)).apply(query)
}
