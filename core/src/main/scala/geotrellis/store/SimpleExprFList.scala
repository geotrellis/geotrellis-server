package geotrellis.store

import java.time.ZonedDateTime

import cats._
import cats.instances.option._
import cats.syntax.functor._
import cats.syntax.option._
import cats.syntax.semigroup._
import cats.syntax.semigroupk._
import cats.instances.function._
import geotrellis.raster.RasterSource
import geotrellis.vector.Extent

object SimpleExprFList {
  val list: List[RasterSource] = Nil

  trait ExprF[A]
  case class Or[A](l: A, r: A) extends ExprF[A]
  case class And[A](l: A, r: A) extends ExprF[A]
  case class Intersection[A](e: Extent) extends ExprF[A]
  case class Contains[A](e: Extent) extends ExprF[A]
  case class At[A](t: ZonedDateTime) extends ExprF[A]
  case class Between[A](from: ZonedDateTime, to: ZonedDateTime) extends ExprF[A]

  case class Fix[F[_]](unfix: F[Fix[F]])

  implicit val semigroupExt: Semigroup[Extent] = { _ combine _ }

  def main(args: Array[String]): Unit = {
    // lets filter the structure
    val list: List[RasterSource] = Nil

    // does not work
    val query: And[ExprF[_ <: Intersection[Nothing]]] = And(
      And(
        Intersection(Extent(0, 0, 2, 2)),
        Intersection(Extent(1, 1, 4, 4))
      ),
      At(ZonedDateTime.now())
    )

    val queryF: Fix[ExprF] =
      Fix(And(
        Fix(And(
          Fix(Intersection(Extent(0, 0, 2, 2))),
          Fix(Intersection(Extent(1, 1, 4, 4)))
        )),
        Fix(At(ZonedDateTime.now()))
      ))

    // ExprF is a functor! lets define a pattern functor
    implicit val exprFFunctor: Functor[ExprF] = new Functor[ExprF] {
      def map[A, B](fa: ExprF[A])(f: A => B): ExprF[B] = fa match {
        case And(l, r) => And(f(l), f(r))
        case Or(l, r) => Or(f(l), f(r))
        case At(v) => At[B](v)
        case Intersection(v) => Intersection[B](v)
      }
    }

    type Algebra[F[_], A] = F[A] => A

    // we can only foldRight since it is a recursive data structure
    // it is called cata
    /** AKA fold right */
    def cata[F[_] : Functor, A](fix: Fix[F])(algebra: F[A] => A): A =
      algebra(fix.unfix.map(cata(_)(algebra)))

    val filterParamsAlg: Algebra[ExprF, List[RasterSource]] = {
      case At(t) => list.filter(rs => rs.metadata.attributes("time") == t.toString)
      case Intersection(e) => list.filter(_.extent.intersects(e))
      case And(e1, e2) => e1 diff e2
      case Or(e1, e2) => e1 ++ e2
    }

    val result = cata(queryF)(filterParamsAlg)
    println(result)

  }
}
