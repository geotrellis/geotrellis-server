package geotrellis.server.ogc.wms

import geotrellis.server.ogc._
import geotrellis.server._
import geotrellis.server.ogc.params.ParamError
import geotrellis.server.ogc.wms.WmsParams.{GetCapabilities, GetMap}
import geotrellis.server.ExtentReification.ops._

import geotrellis.spark._
import geotrellis.proj4.{LatLng, WebMercator}
import geotrellis.raster.{MultibandTile, Raster, RasterExtent}
import com.azavea.maml.error._
import com.azavea.maml.eval._
import org.http4s._
import org.http4s.dsl.Http4sDsl
import org.http4s.dsl.io._
import org.http4s.circe._
import org.http4s.scalaxml._
import org.http4s.implicits._
import cats.data.Validated._
import cats.implicits._
import cats.effect._
import _root_.io.circe._
import _root_.io.circe.syntax._
import com.typesafe.scalalogging.LazyLogging

import java.net.{URI, URL}

class WmsService(
  model: RasterSourcesModel,
  serviceUrl: URL,
  serviceMetadata: opengis.wms.Service
)(implicit contextShift: ContextShift[IO])
  extends Http4sDsl[IO]
     with LazyLogging {

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
      println(req)

      WmsParams(req.multiParams) match {
        case Invalid(errors) =>
          val msg = ParamError.generateErrorMessage(errors.toList)
          logger.warn(msg)
          BadRequest(msg)

        case Valid(wmsReq: GetCapabilities) =>
          Ok.apply(new CapabilitiesView(model, serviceUrl, serviceMetadata, defaultCrs = LatLng).toXML)

        case Valid(wmsReq: GetMap) =>
          val re = RasterExtent(wmsReq.boundingBox, wmsReq.width, wmsReq.height)
          model.getLayer(wmsReq.crs, wmsReq.layers.headOption, wmsReq.styles.headOption).map { layer =>
            val evalExtent = layer match {
              case sl@SimpleLayer(_, _, _, _, _) =>
                LayerExtent.identity(sl)
              case sl@MapAlgebraLayer(_, _, _, parameters, expr, _) =>
                LayerExtent(IO.pure(expr), IO.pure(parameters), Interpreter.DEFAULT)
            }

            val evalHisto = layer match {
              case sl@SimpleLayer(_, _, _, _, _) =>
                LayerHistogram.identity(sl, 512)
              case sl@MapAlgebraLayer(_, _, _, parameters, expr, _) =>
                LayerHistogram(IO.pure(expr), IO.pure(parameters), Interpreter.DEFAULT, 512)
            }

            (evalExtent(re.extent, re.cellSize), evalHisto).parMapN {
              case (Valid(mbtile), Valid(hists)) =>
                Valid((mbtile, hists))
              case (Invalid(errs), _) =>
                Invalid(errs)
              case (_, Invalid(errs)) =>
                Invalid(errs)
            }.attempt flatMap {
              case Right(Valid((mbtile, hists))) => // success
                println(hists.head.statistics)
                val rendered = Render(mbtile, layer.style, wmsReq.format, hists)
                Ok(rendered)
              case Right(Invalid(errs)) => // maml-specific errors
                logger.debug(errs.toList.toString)
                BadRequest(errs.asJson)
              case Left(err) =>            // exceptions
                logger.error(err.toString, err)
                InternalServerError(err.toString)
            }
          }.getOrElse(BadRequest("No such layer"))
      }

    case req =>
      logger.warn(s"""Recv'd UNHANDLED request: $req""")
      BadRequest("Don't know what that is")
  }
}
