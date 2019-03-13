package geotrellis.server.ogc.wmts

import geotrellis.server._
import geotrellis.server.ogc._
import geotrellis.server.ogc.params.ParamError
import geotrellis.server.ogc.wmts.WmtsParams.{GetCapabilities, GetTile}
import com.azavea.maml.eval._

import org.http4s._
import org.http4s.dsl.Http4sDsl
import org.http4s.circe._
import org.http4s.scalaxml._
import cats.data.Validated._
import cats.effect._
import cats.implicits._
import _root_.io.circe.syntax._
import com.typesafe.scalalogging.LazyLogging

import java.net.URL

class WmtsService(
  rasterSourcesModel: RasterSourcesModel,
  tileMatrixModel: TileMatrixModel,
  serviceUrl: URL,
  serviceMetadata: ows.ServiceMetadata
)(implicit contextShift: ContextShift[IO]) extends Http4sDsl[IO] with LazyLogging {

  def handleError[Result](result: Either[Throwable, Result])(implicit ee: EntityEncoder[IO, Result]): IO[Response[IO]] = result match {
    case Right(res) =>
      logger.trace(res.toString)
      Ok(res)

    case Left(err) =>
      logger.error(err.toString)
      InternalServerError(err.toString)
  }

  def routes: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> Root =>
      logger.debug(s"WMTS Request received: $req")

      WmtsParams(req.multiParams) match {
        case Invalid(errors) =>
          val msg = ParamError.generateErrorMessage(errors.toList)
          logger.warn(msg)
          BadRequest(msg)

        case Valid(wmtsReq: GetCapabilities) =>
          Ok.apply(new CapabilitiesView(serviceMetadata, rasterSourcesModel, tileMatrixModel, serviceUrl).toXML)

        case Valid(wmtsReq: GetTile) =>
          val tileCol = wmtsReq.tileCol
          val tileRow = wmtsReq.tileRow
          val style = wmtsReq.style
          val layerName = wmtsReq.layer
          (for {
            crs <- tileMatrixModel.getCrs(wmtsReq.tileMatrixSet)
            layoutDefinition <- tileMatrixModel.getLayoutDefinition(wmtsReq.tileMatrixSet, wmtsReq.tileMatrix)
            layer <- rasterSourcesModel.getWmtsLayer(crs, layerName, layoutDefinition, style)
          } yield {
            val evalWmts = layer match {
              case sl@SimpleWmtsLayer(_, _, _, _, _, _) =>
                LayerTms.identity(sl)
              case sl@MapAlgebraWmtsLayer(_, _, _, _, parameters, expr, _) =>
                LayerTms(IO.pure(expr), IO.pure(parameters), Interpreter.DEFAULT)
              case _ => throw new Exception("This shouldn't happen")
            }

            val evalHisto = layer match {
              case sl@SimpleWmtsLayer(_, _, _, _, _, _) =>
                LayerHistogram.identity(sl, 512)
              case sl@MapAlgebraWmtsLayer(_, _, _, _, parameters, expr, _) =>
                LayerHistogram(IO.pure(expr), IO.pure(parameters), Interpreter.DEFAULT, 512)
              case _ => throw new Exception("This shouldn't happen")
            }

            (evalWmts(0, tileCol, tileRow), evalHisto).parMapN {
              case (Valid(mbtile), Valid(hists)) =>
                Valid((mbtile, hists))
              case (Invalid(errs), _) =>
                Invalid(errs)
              case (_, Invalid(errs)) =>
                Invalid(errs)
            }.attempt flatMap {
              case Right(Valid((mbtile, hists))) => // success
                val rendered = Render(mbtile, layer.style, wmtsReq.format, hists)
                Ok(rendered)
              case Right(Invalid(errs)) => // maml-specific errors
                logger.debug(errs.toList.toString)
                BadRequest(errs.asJson)
              case Left(err) =>            // exceptions
                logger.error(err.toString, err)
                InternalServerError(err.toString)
            }
          }).getOrElse(BadRequest("No such layer"))
      }

    case req =>
      logger.warn(s"""Recv'd UNHANDLED request: $req""")
      BadRequest("Don't know what that is")
  }
}
