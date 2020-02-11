package geotrellis.store

import geotrellis.raster.RasterSource
import geotrellis.vector.{Extent, Geometry}
import cats._
import cats.syntax.option._
import cats.instances.function._
import cats.syntax.functor._

import java.time.ZonedDateTime

trait QueryF[A]
case class Or[A](l: A, r: A) extends QueryF[A]
case class And[A](l: A, r: A) extends QueryF[A]
case class Intersects[A](g: Geometry) extends QueryF[A]
case class Contains[A](g: Geometry) extends QueryF[A]
case class Covers[A](g: Geometry) extends QueryF[A]
case class At[A](t: ZonedDateTime, fieldName: Symbol = 'time) extends QueryF[A]
case class Between[A](from: ZonedDateTime, to: ZonedDateTime, fieldName: Symbol = 'time) extends QueryF[A]

// fixed point to derive fancy types
case class Fix[F[_]](unfix: F[Fix[F]])

object QueryF {
  implicit val semigroupGeometry: Semigroup[Geometry] = { _ union _ }

  implicit val queryFFunctor: Functor[QueryF] = new Functor[QueryF] {
    def map[A, B](fa: QueryF[A])(f: A => B): QueryF[B] = fa match {
      case And(l, r) => And(f(l), f(r))
      case Or(l, r) => Or(f(l), f(r))
      case At(v, fn) => At[B](v, fn)
      case Between(t1, t2, fn) => Between[B](t1, t2, fn)
      case Intersects(v) => Intersects[B](v)
      case Contains(v) => Contains[B](v)
      case Covers(v) => Covers[B](v)
    }
  }

  type Algebra[F[_], A] = F[A] => A

  // we can only foldRight since it is a recursive data structure
  // it is called cata
  /** AKA fold right */
  def cata[F[_] : Functor, A](fix: Fix[F])(algebra: F[A] => A): A =
    algebra(fix.unfix.map(cata(_)(algebra)))

  def listAlg(list: List[RasterSource]): Algebra[QueryF, List[RasterSource]] = {
    case At(t, fn) => list.filter(rs => rs.metadata.attributes(fn.name) == t.toString)
    case Intersects(e) => list.filter(_.extent.intersects(e))
    case Covers(e) => list.filter(_.extent.covers(e))
    case Contains(e) => list.filter(_.extent.covers(e))
    case And(e1, e2) => e1 diff e2
    case Or(e1, e2) => e1 ++ e2
  }
}

trait SourceCatalog {
  val list: List[RasterSource]

  def find[A](query: QueryF[A]): List[RasterSource]
}

object Main {
  def main(arg: Array[String]): Unit = {
    val list: List[RasterSource] = Nil

    val queryF: Fix[QueryF] =
      Fix(And(
        Fix(And(
          Fix(Intersects(Extent(0, 0, 2, 2))),
          Fix(Intersects(Extent(1, 1, 4, 4)))
        )),
        Fix(At(ZonedDateTime.now()))
      ))


    val res = QueryF.cata(queryF)(QueryF.listAlg(list))

    println(res)
  }
}