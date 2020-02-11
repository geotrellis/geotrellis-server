package geotrellis.store

import geotrellis.vector.Extent

import cats._
import cats.syntax.semigroup._
import cats.syntax.semigroupk._
import cats.instances.option._
import cats.syntax.option._

import java.time.ZonedDateTime

object SimpleExpr {
  trait Expr
  case class Or(l: Expr, r: Expr) extends Expr
  case class And(l: Expr, r: Expr) extends Expr
  case class Ext(e: Extent) extends Expr
  case class At(t: ZonedDateTime) extends Expr

  implicit val semigroupExt: Semigroup[Extent] = { _ combine _ }

  def main(args: Array[String]): Unit = {
    // lets fill in thi structure
    case class FilterParams(ext: Option[Extent], time: Option[ZonedDateTime]) {
      def and(other: FilterParams): FilterParams = {
        FilterParams({
          (ext, other.ext) match {
            case (Some(l), Some(r)) => l.intersection(r)
            case (l, _) => l
            case (_, r) => r
            case _ => None
          }
        }, time <+> other.time)
      }

      def or(other: FilterParams): FilterParams = FilterParams(ext |+| other.ext, time <+> other.time)
    }

    val query = And(
      And(
        Ext(Extent(0, 0, 2, 2)),
        Ext(Extent(1, 1, 4, 4))
      ),
      At(ZonedDateTime.now())
    )

    def eval(e: Expr): FilterParams =
      e match {
        case At(t)         => FilterParams(none, t.some)
        case Ext(e)        => FilterParams(e.some, none)
        case And(e1, e2)   => eval(e1).and(eval(e2))
        case Or(e1, e2)    => eval(e1).or(eval(e2))
      }

    val result = eval(query)

    println(result)
  }
}
