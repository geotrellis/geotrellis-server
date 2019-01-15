package geotrellis.server.example.ndvi

import geotrellis.server._
import geotrellis.raster._
import geotrellis.raster.render._
import com.azavea.maml.ast._
import com.azavea.maml.ast.codec.tree._
import com.azavea.maml.eval._

import org.http4s._
import org.http4s.dsl.Http4sDsl
import org.http4s.circe._
import _root_.io.circe._
import _root_.io.circe.parser._
import _root_.io.circe.syntax._
import cats._
import cats.implicits._
import cats.data._
import Validated._
import cats.effect._
import com.typesafe.scalalogging.LazyLogging

import java.net.URLDecoder

class NdviService[F[_], Par[_], Param](
  interpreter: BufferingInterpreter = BufferingInterpreter.DEFAULT
)(implicit F: ConcurrentEffect[F],
           P: Parallel[F, Par],
           contextShift: ContextShift[F],
           enc: Encoder[Param],
           dec: Decoder[Param],
           mr: TmsReification[Param]) extends Http4sDsl[F] with LazyLogging {

  object ParamBindings {
    def unapply(str: String): Option[Map[String, Param]] =
      decode[Map[String, Param]](str) match {
        case Right(res) => Some(res)
        case Left(_) => None
      }
  }

  implicit val redQueryParamDecoder: QueryParamDecoder[Param] =
    QueryParamDecoder[String].map { str => decode[Param](URLDecoder.decode(str, "UTF-8")).right.get }
  object RedQueryParamMatcher extends QueryParamDecoderMatcher[Param]("red")
  object NirQueryParamMatcher extends QueryParamDecoderMatcher[Param]("nir")

  implicit val expressionDecoder = jsonOf[F, Expression]

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

  final val eval = LayerTms.curried(ndvi, interpreter)

  // http://0.0.0.0:9000/{z}/{x}/{y}.png
  def routes: HttpRoutes[F] = HttpRoutes.of {
    // Matching json in the query parameter is a bad idea.
    case req @ GET -> Root / IntVar(z) / IntVar(x) / IntVar(y) ~ "png" :? RedQueryParamMatcher(red) +& NirQueryParamMatcher(nir) =>
      val paramMap = Map("red" -> red, "nir" -> nir)

      eval(paramMap, z, x, y).attempt flatMap {
        case Right(Valid(mbtile)) =>
          // Image results have multiple bands. We need to pick one
          Ok(mbtile.band(0).renderPng(ColorRamps.Viridis).bytes)
        case Right(Invalid(errs)) =>
          logger.debug(errs.toList.toString)
          BadRequest(errs.asJson)
        case Left(err) =>
          logger.debug(err.toString, err)
          InternalServerError(err.toString)
      }
  }
}

