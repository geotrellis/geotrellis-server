package geotrellis.server.ogc.wms

import geotrellis.spark._
import geotrellis.proj4.WebMercator
import geotrellis.server.ogc.params.ParamError
import geotrellis.server.ogc.wms.WmsParams.{GetCapabilities, GetMap}
import org.http4s._
import org.http4s.dsl.Http4sDsl
import org.http4s.dsl.io._
import org.http4s.scalaxml._
import org.http4s.implicits._
import cats.data.Validated
import cats.effect._
import com.typesafe.scalalogging.LazyLogging
import java.net.URI

import geotrellis.raster.{MultibandTile, Raster}

object WmsService {
  type MetadataCatalog = Map[String, (Seq[Int], Option[TileLayerMetadata[SpatialKey]])]
}

class WmsService(catalog: URI, authority: String, port: Int) extends Http4sDsl[IO] with LazyLogging {
  /** Should be generated from the catalog */
  lazy val model = RasterSourcesModel(Map())

  def handleError[Result](result: Either[Throwable, Result])(implicit ee: EntityEncoder[IO, Result]): IO[Response[IO]] = result match {
    case Right(res) =>
      logger.trace(res.toString)
      Ok(res)

    case Left(err) =>
      logger.error(err.toString)
      InternalServerError(err.toString)
  }

  def routes: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> Root / "wms" =>
      WmsParams(req.multiParams) match {
        case Validated.Invalid(errors) =>
          val msg = ParamError.generateErrorMessage(errors.toList)
          logger.warn(msg)
          BadRequest(msg)

        case Validated.Valid(wmsReq: GetCapabilities) =>
          Ok.apply(new CapabilitiesView(model).toXML)

        case Validated.Valid(wmsReq: GetMap) =>
          val raster: Option[Raster[MultibandTile]] = model.getMap(wmsReq)
          val png = model.getMapWithColorRamp(wmsReq)
          Ok(s"""GET Request: $wmsReq""")
      }

    case req =>
      logger.warn(s"""Recv'd UNHANDLED request: $req""")
      BadRequest("Don't know what that is")
  }
}
