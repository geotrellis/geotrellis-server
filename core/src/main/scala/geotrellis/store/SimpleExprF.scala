package geotrellis.store

import geotrellis.vector.Extent

import cats._
import cats.instances.option._
import cats.syntax.option._
import cats.syntax.semigroup._
import cats.syntax.semigroupk._
import cats.syntax.functor._
import cats.instances.function._

import java.time.ZonedDateTime

object SimpleExprF {
  trait ExprF[A]
  case class Or[A](l: A, r: A) extends ExprF[A]
  case class And[A](l: A, r: A) extends ExprF[A]
  case class Ext[A](e: Extent) extends ExprF[A]
  case class At[A](t: ZonedDateTime) extends ExprF[A]

  case class Fix[F[_]](unfix: F[Fix[F]])

  implicit val semigroupExt: Semigroup[Extent] = { _ combine _ }

  def main(args: Array[String]): Unit = {
    // lets fill in thi structure
    case class FilterParams(ext: Option[Extent], time: Option[ZonedDateTime]) {
      def and(other: FilterParams): FilterParams = {
        FilterParams({
          (ext, other.ext) match {
            case (Some(l), Some(r)) => l.intersection(r)
            case (Some(l), _) => l.some
            case (_, Some(r)) => r.some
            case _ => None
          }
        }, time <+> other.time)
      }

      def or(other: FilterParams): FilterParams = FilterParams(ext |+| other.ext, time <+> other.time)
    }

    // does not work
    val query: And[ExprF[_ <: Ext[Nothing]]] = And(
      And(
        Ext(Extent(0, 0, 2, 2)),
        Ext(Extent(1, 1, 4, 4))
      ),
      At(ZonedDateTime.now())
    )

    val queryF: Fix[ExprF] =
      Fix(And(
        Fix(And(
          Fix(Ext(Extent(0, 0, 2, 2))),
          Fix(Ext(Extent(1, 1, 4, 4)))
        )),
        Fix(At(ZonedDateTime.now()))
      ))

    // ExprF is a functor! lets define a pattern functor
    implicit val exprFFunctor: Functor[ExprF] = new Functor[ExprF] {
      def map[A, B](fa: ExprF[A])(f: A => B): ExprF[B] = fa match {
        case And(l, r) => And(f(l), f(r))
        case Or(l, r)  => Or(f(l), f(r))
        case At(v) => At[B](v)
        case Ext(v) => Ext[B](v)
      }
    }

    type Algebra[F[_], A] = F[A] => A

    // we can only foldRight since it is a recursive data structure
    // it is called cata
    /** AKA fold right */
    def cata[F[_]: Functor, A](fix: Fix[F])(algebra: F[A] => A): A =
      algebra(fix.unfix.map(cata(_)(algebra)))

    val filterParamsAlg: Algebra[ExprF, FilterParams] = {
      case At(t)         => FilterParams(none, t.some)
      case Ext(e)        => FilterParams(e.some, none)
      case And(e1, e2)   => e1 and e2
      case Or(e1, e2)    => e1 or e2
    }

    val result = cata(queryF)(filterParamsAlg)
    println(result)

    val queryF2: Fix[ExprF] =
      Fix(And(
        Fix(And(
          Fix(Ext(Extent(0, 0, 2, 2))),
          Fix(At(ZonedDateTime.now()))
        )),
        Fix(At(ZonedDateTime.now()))
      ))

    // lets go further
    type CoAlgebra[F[_], A] = A => F[A]

    /** AKA unfold */
    def ana[F[_]: Functor, A](unfix: A)(coalgebra: A => F[A]): Fix[F] =
      Fix(coalgebra(unfix).map(ana(_)(coalgebra)))

    val filterParamsCoAlg: CoAlgebra[ExprF, FilterParams] = {
      case FilterParams(Some(ext), Some(dt)) => And(FilterParams(ext.some, none), FilterParams(none, dt.some))
      case FilterParams(Some(ext), _) => Ext(ext)
      case FilterParams(_, Some(dt)) => At(dt)
      case _ => ???
    }

    val original = ana(FilterParams(Extent(0, 0, 1, 1).some, ZonedDateTime.now().some))(filterParamsCoAlg)

    println(original)


    // voi la
    def hylo[F[_]: Functor, A, B](algebra: Algebra[F, B], coalgebra: CoAlgebra[F, A]): A => B =
      new (A => B) { kernel: (A => B) =>
        def apply(init: A): B =
          algebra(coalgebra(init).fmap(kernel))
      }

    def hylo2[F[_]: Functor, A, B](a: A)(algebra: Algebra[F, B], coalgebra: CoAlgebra[F, A]): B =
      algebra(coalgebra(a) map (hylo2(_)(algebra, coalgebra)))
  }
}
