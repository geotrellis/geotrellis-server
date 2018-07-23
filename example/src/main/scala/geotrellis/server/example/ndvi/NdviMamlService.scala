package geotrellis.server.example.ndvi

import geotrellis.server.core.maml._
import MamlReification.ops._

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


class ExampleNdviMamlService[Param](
  interpreter: BufferingInterpreter = BufferingInterpreter.DEFAULT
)(implicit t: Timer[IO],
           pd: Decoder[Param],
           mr: MamlReification[Param]) extends Http4sDsl[IO] with LazyLogging {

  object ParamBindings {
    def unapply(str: String): Option[Map[String, Param]] =
      decode[Map[String, Param]](str) match {
        case Right(res) => Some(res)
        case Left(_) => None
      }
  }

  implicit val redQueryParamDecoder: QueryParamDecoder[Param] =
    QueryParamDecoder[String].map(decode[Param](_).right.get)
  object RedQueryParamMatcher extends QueryParamDecoderMatcher[Param]("red")
  object NirQueryParamMatcher extends QueryParamDecoderMatcher[Param]("nir")

  implicit val expressionDecoder = jsonOf[IO, Expression]

  final val ndvi: Expression =
    Division(List(
      Subtraction(List(
        RasterVar("red"),
        RasterVar("nir"))),
      Addition(List(
        RasterVar("red"),
        RasterVar("nir"))
      ))
    )

  val reification = implicitly[MamlReification[Param]]

  def routes: HttpService[IO] = HttpService[IO] {
    case req @ GET -> Root / IntVar(z) / IntVar(x) / IntVar(y) :? RedQueryParamMatcher(red) +& NirQueryParamMatcher(nir) =>
      //val red = CogNode(new URI(""), 2, None)
      //val nir = CogNode(new URI(""), 3, None)
      val paramMap = Map("red" -> red, "nir" -> nir)
      (for {
        vars      <- IO.pure { Vars.varsWithBuffer(ndvi) }
        params    <- vars.toList.parTraverse { case (varName, (_, buffer)) =>
                       paramMap(varName)
                         .tmsReification(buffer)(t)(z, x, y)
                         .map(varName -> _)
                     }.map { _.toMap }
        reified   <- IO.pure { Expression.bindParams(ndvi, params) }
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

