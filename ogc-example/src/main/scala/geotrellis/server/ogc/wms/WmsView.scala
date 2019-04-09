package geotrellis.server.ogc.wms

import geotrellis.server._
import geotrellis.server.ogc._
import geotrellis.server.ogc.params.ParamError
import geotrellis.server.ogc.wms.WmsParams.{GetCapabilities, GetMap}
import geotrellis.raster.RasterExtent
import geotrellis.raster.histogram._

import com.azavea.maml.error._
import com.azavea.maml.eval._
import geotrellis.contrib.vlm.RasterSource
import geotrellis.contrib.vlm.geotiff._
import geotrellis.contrib.vlm.avro._
import geotrellis.raster.histogram.Histogram
import geotrellis.spark.tiling._
import geotrellis.spark._
import geotrellis.proj4._
import geotrellis.raster.render.{ColorMap, ColorRamp, Png}
import geotrellis.raster._
import com.typesafe.scalalogging.LazyLogging
import geotrellis.spark.io.s3.AmazonS3Client
import scalaxb.CanWriteXML
import org.http4s.scalaxml._
import org.http4s.circe._
import org.http4s._, org.http4s.dsl.io._, org.http4s.implicits._
import _root_.io.circe.syntax._
import cats._, cats.implicits._
import cats.effect._
import cats.data.Validated._
import com.github.blemale.scaffeine.{Cache, Scaffeine}

import java.net.URL
import scala.concurrent.duration._

class WmsView(wmsModel: WmsModel, serviceUrl: URL) extends LazyLogging {

  private val histoCache: Cache[OgcLayer, Interpreted[List[Histogram[Double]]]] =
    Scaffeine()
      .recordStats()
      .expireAfterWrite(1.hour)
      .maximumSize(500)
      .build[OgcLayer, Interpreted[List[Histogram[Double]]]]()


  def responseFor(req: Request[IO])(implicit cs: ContextShift[IO]): IO[Response[IO]] = {
    WmsParams(req.multiParams) match {
      case Invalid(errors) =>
        val msg = ParamError.generateErrorMessage(errors.toList)
        logger.warn(msg)
        BadRequest(msg)

      case Valid(wmsReq: GetCapabilities) =>
        Ok.apply(new CapabilitiesView(wmsModel, serviceUrl).toXML)

      case Valid(wmsReq: GetMap) =>
        val re = RasterExtent(wmsReq.boundingBox, wmsReq.width, wmsReq.height)
        wmsModel.getLayer(wmsReq.crs, wmsReq.layers.headOption, wmsReq.styles.headOption).map { layer =>
          val evalExtent = layer match {
            case sl@SimpleOgcLayer(_, _, _, _, _) =>
              LayerExtent.identity(sl)
            case sl@MapAlgebraOgcLayer(_, _, _, parameters, expr, _) =>
              LayerExtent(IO.pure(expr), IO.pure(parameters), Interpreter.DEFAULT)
          }

          val evalHisto = layer match {
            case sl@SimpleOgcLayer(_, _, _, _, _) =>
              LayerHistogram.identity(sl, 512)
            case sl@MapAlgebraOgcLayer(_, _, _, parameters, expr, _) =>
              LayerHistogram(IO.pure(expr), IO.pure(parameters), Interpreter.DEFAULT, 512)
          }

          val histIO = for {
            cached <- IO { histoCache.getIfPresent(layer) }
            hist   <- cached match {
                        case Some(h) => IO.pure(h)
                        case None => evalHisto
                      }
            _ <-  IO { histoCache.put(layer, hist) }
          } yield hist

          (evalExtent(re.extent, re.cellSize), histIO).parMapN {
            case (Valid(mbtile), Valid(hists)) =>
              Valid((mbtile, hists))
            case (Invalid(errs), _) =>
              Invalid(errs)
            case (_, Invalid(errs)) =>
              Invalid(errs)
          }.attempt flatMap {
            case Right(Valid((mbtile, hists))) => // success
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
  }
}
