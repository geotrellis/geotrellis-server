package geotrellis.server.ogc.wmts

import geotrellis.server._
import geotrellis.server.ogc._
import geotrellis.server.ogc.params.ParamError
import geotrellis.server.ogc.wmts.WmtsParams.{GetCapabilities, GetTile}
import com.azavea.maml.eval._

import geotrellis.contrib.vlm.RasterSource
import geotrellis.contrib.vlm.geotiff._
import geotrellis.contrib.vlm.avro._
import geotrellis.spark.tiling._
import geotrellis.spark._
import geotrellis.proj4._
import geotrellis.raster.render.{ColorMap, ColorRamp, Png}
import geotrellis.raster._
import com.typesafe.scalalogging.LazyLogging
import geotrellis.spark.io.s3.AmazonS3Client
import scalaxb.CanWriteXML
import org.http4s.scalaxml._
import org.http4s._, org.http4s.dsl.io._, org.http4s.implicits._
import org.http4s.circe._
import _root_.io.circe.syntax._
import cats._, cats.implicits._
import cats.effect._
import cats.data.Validated._

import java.io.File
import java.net._

class WmtsView(wmtsModel: WmtsModel, serviceUrl: URL) extends LazyLogging {

  def responseFor(req: Request[IO])(implicit cs: ContextShift[IO]): IO[Response[IO]] = {
      WmtsParams(req.multiParams) match {
        case Invalid(errors) =>
          val msg = ParamError.generateErrorMessage(errors.toList)
          logger.warn(msg)
          BadRequest(msg)

        case Valid(wmtsReq: GetCapabilities) =>
          Ok(new CapabilitiesView(wmtsModel, serviceUrl).toXML)

        case Valid(wmtsReq: GetTile) =>
          val tileCol = wmtsReq.tileCol
          val tileRow = wmtsReq.tileRow
          val style = wmtsReq.style
          val layerName = wmtsReq.layer
          (for {
            crs <- wmtsModel.getMatrixCrs(wmtsReq.tileMatrixSet)
            layoutDefinition <- wmtsModel.getMatrixLayoutDefinition(wmtsReq.tileMatrixSet, wmtsReq.tileMatrix)
            layer <- wmtsModel.getLayer(crs, layerName, layoutDefinition, style)
          } yield {
            val evalWmts = layer match {
              case sl@SimpleTiledOgcLayer(_, _, _, _, _, _) =>
                LayerTms.identity(sl)
              case sl@MapAlgebraTiledOgcLayer(_, _, _, _, parameters, expr, _) =>
                LayerTms(IO.pure(expr), IO.pure(parameters), ConcurrentInterpreter.DEFAULT[IO])
            }

            val evalHisto = layer match {
              case sl@SimpleTiledOgcLayer(_, _, _, _, _, _) =>
                LayerHistogram.identity(sl, 512)
              case sl@MapAlgebraTiledOgcLayer(_, _, _, _, parameters, expr, _) =>
                LayerHistogram(IO.pure(expr), IO.pure(parameters), ConcurrentInterpreter.DEFAULT[IO], 512)
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
          }).getOrElse(BadRequest(s"Layer (${layerName}) not found"))
      }
  }
}
