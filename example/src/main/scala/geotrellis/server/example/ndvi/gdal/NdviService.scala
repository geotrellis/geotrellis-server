package geotrellis.server.example.ndvi.gdal

import geotrellis.server._
import TmsReification.ops._
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
import cats.data._
import Validated._
import cats.implicits._
import cats.effect._
import com.typesafe.scalalogging.LazyLogging
import geotrellis.raster._
import geotrellis.raster.render._
import geotrellis.server.gdal.GDALNode


class NdviService[Param](
  interpreter: BufferingInterpreter = BufferingInterpreter.DEFAULT
)(implicit contextShift: ContextShift[IO],
           enc: Encoder[Param],
           dec: Decoder[Param],
           mr: TmsReification[Param]) extends Http4sDsl[IO] with LazyLogging {

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

  final val eval = LayerTms.curried(ndvi, interpreter)

  // http://0.0.0.0:9000/{z}/{x}/{y}.png
  def routes: HttpRoutes[IO] = HttpRoutes.of {
    // Matching json in the query parameter is a bad idea.
    case req @ GET -> Root / IntVar(z) / IntVar(x) / IntVar(y) ~ "png" /*:? RedQueryParamMatcher(red) +& NirQueryParamMatcher(nir)*/ =>

      println(s"geotrellis.contrib.vlm.gdal.GDAL.cache.size: ${geotrellis.contrib.vlm.gdal.GDAL.cache.asMap.size}")

      val paramMap = Map(
        "red" -> GDALNode(new java.net.URI("/Users/daunnc/subversions/git/github/geotrellis-landsat-tutorial/data/r-g-nir.tif"), 0, None).asInstanceOf[Param],
        "nir" -> GDALNode(new java.net.URI("/Users/daunnc/subversions/git/github/geotrellis-landsat-tutorial/data/r-g-nir.tif"), 2, None).asInstanceOf[Param]
      )

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

