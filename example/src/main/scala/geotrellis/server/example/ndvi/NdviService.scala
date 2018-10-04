package geotrellis.server.example.ndvi

import geotrellis.server.core.maml.{MamlTms, MamlHistogram}
import geotrellis.server.core.maml.persistence._
import MamlStore.ops._
import geotrellis.server.core.maml.reification._
import MamlTmsReification.ops._

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


class NdviService[Param](
  interpreter: BufferingInterpreter = BufferingInterpreter.DEFAULT
)(implicit contextShift: ContextShift[IO],
           enc: Encoder[Param],
           dec: Decoder[Param],
           mr: MamlTmsReification[Param]) extends Http4sDsl[IO] with LazyLogging {

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

  final val eval = MamlTms.curried(ndvi, interpreter)

  def routes: HttpRoutes[IO] = HttpRoutes.of {
    // Matching json in the query parameter is a bad idea.
    case req @ GET -> Root / IntVar(z) / IntVar(x) / IntVar(y) ~ "png" :? RedQueryParamMatcher(red) +& NirQueryParamMatcher(nir) =>
      val paramMap = Map("red" -> red, "nir" -> nir)

      eval(paramMap, z, x, y).attempt flatMap {
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

