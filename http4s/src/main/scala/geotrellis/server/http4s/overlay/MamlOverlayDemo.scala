package geotrellis.server.http4s.maml

import geotrellis.server.core.maml._

import com.azavea.maml.util.Vars
import com.azavea.maml.ast._
import com.azavea.maml.ast.codec.tree._
import com.azavea.maml.eval._
import org.http4s._
import org.http4s.dsl.Http4sDsl
import org.http4s.circe._
import io.circe._
import io.circe.parser._
import io.circe.syntax._
import cats._
import cats.data._, Validated._
import cats.implicits._
import cats.effect._
import com.typesafe.scalalogging.LazyLogging
import geotrellis.raster._
import geotrellis.raster.render._

import scala.math._
import java.net.URI
import java.util.{UUID, NoSuchElementException}
import scala.util.Try
import scala.collection.mutable


class MamlOverlayDemo[Param](
  interpreter: BufferingInterpreter = BufferingInterpreter.DEFAULT
)(implicit t: Timer[IO],
           pd: Decoder[Param],
           mr: MamlReification[Param]) extends Http4sDsl[IO] with LazyLogging {

  object ParamBindings {
    def unapply(str: String): Option[List[(Param, Double)]] =
      decode[List[(Param, Double)]](str) match {
        case Right(res) => Some(res)
        case Left(_) => None
      }
  }

  implicit val expressionDecoder = jsonOf[IO, Expression]

  final def overlayExpression(args: List[(Param, Double)]): Expression = {
    val rasterVars = (0 to args.size - 1).toList map({ num => RasterVar(num.toString) })
    WeightedOverlay(rasterVars, args.map(_._2))
  }

  final def overlayParams(args: List[(Param, Double)]): Map[String, Param] =
    args.map(_._1).zipWithIndex.map({ case (param, idx) => idx.toString -> param }).toMap

  val reification = implicitly[MamlReification[Param]]

  def routes: HttpService[IO] = HttpService[IO] {
    case req @ GET -> Root / IntVar(z) / IntVar(x) / IntVar(y) =>
      val args: List[(Param, Double)] = ???
      val paramMap = overlayParams(args)
      (for {
        expr      <- IO.pure { overlayExpression(args) }
        vars      <- IO.pure { Vars.varsWithBuffer(expr) }
        params    <- vars.toList.parTraverse { case (varName, (_, buffer)) =>
                       reification.tmsReification(paramMap(varName), buffer)(t)(z, x, y).map(varName -> _)
                     } map { _.toMap }
        reified   <- IO.pure { Expression.bindParams(expr, params) }
      } yield reified.andThen(interpreter(_)).andThen(_.as[Tile])).attempt flatMap {
        case Right(Valid(tile)) =>
          Ok(tile.renderPng(ColorRamps.Viridis).bytes)
        case Right(Invalid(errs)) =>
          logger.debug(errs.toList.toString)
          BadRequest(errs.asJson)
        case Left(MamlStore.ExpressionNotFound(err)) =>
          logger.info(err.toString)
          NotFound()
        case Left(err) =>
          logger.debug(err.toString, err)
          InternalServerError(err.toString)
      }
  }
}

