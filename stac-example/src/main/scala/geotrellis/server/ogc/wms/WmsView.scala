/*
 * Copyright 2020 Azavea
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package geotrellis.server.ogc.wms

import geotrellis.store.query._
import geotrellis.server._
import geotrellis.server.ogc._
import geotrellis.server.ogc.utils._
import geotrellis.server.ogc.params.ParamError
import geotrellis.server.ogc.wms.WmsParams.{GetCapabilities, GetMap}
import geotrellis.server.utils._

import geotrellis.raster.RasterExtent
import geotrellis.raster.{io => _, _}
import com.azavea.maml.error._
import com.azavea.maml.eval._
import org.http4s.scalaxml._
import org.http4s.circe._
import org.http4s._
import org.http4s.dsl.Http4sDsl
import _root_.io.circe.syntax._
import cats.effect._
import cats.Parallel
import cats.syntax.apply._
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.syntax.applicative._
import cats.syntax.option._
import cats.syntax.applicativeError._
import cats.syntax.parallel._
import cats.data.Validated._
import io.chrisdavenport.log4cats.Logger
import com.github.blemale.scaffeine.{Cache, Scaffeine}
import org.backuity.ansi.AnsiFormatter.FormattedHelper
import opengis._
import org.http4s.headers.`Content-Type`
import scalaxb._

import java.net.URL

import scala.concurrent.duration._
import scala.xml.Elem

class WmsView[F[_]: Concurrent: Parallel: ApplicativeThrow: Logger](
  wmsModel: WmsModel[F],
  serviceUrl: URL
) extends Http4sDsl[F] {
  val logger = Logger[F]

  val extendedCapabilities: List[DataRecord[Elem]] = {
    val targetCells = DataRecord(
      None,
      "target".some,
      DataRecord("all").toXML ++
      DataRecord("data").toXML ++
      DataRecord("nodata").toXML ++
      DataRecord(None, "default".some, "all").toXML
    )

    val focalHillshade: Elem = ExtendedElement(
      "FocalHillshade",
      DataRecord("zFactor"),
      DataRecord("azimuth"),
      DataRecord("altitude"),
      targetCells
    )

    val focalSlope: Elem = ExtendedElement(
      "FocalSlope",
      DataRecord("zFactor"),
      targetCells
    )

    val channels            = "Red" :: "Green" :: "Blue" :: Nil
    def clamp(band: String) =
      DataRecord(
        None,
        s"Clamp$band".some,
        DataRecord(s"clampMin$band").toXML ++
        DataRecord(s"clampMax$band").toXML
      )

    def normalize(band: String) =
      DataRecord(
        None,
        s"Normalize$band".some,
        DataRecord(s"normalizeOldMin$band").toXML ++
        DataRecord(s"normalizeOldMax$band").toXML ++
        DataRecord(s"normalizeNewMin$band").toXML ++
        DataRecord(s"normalizeNewMax$band").toXML
      )

    def rescale(band: String) =
      DataRecord(
        None,
        s"Rescale$band".some,
        DataRecord(s"rescaleNewMin$band").toXML ++
        DataRecord(s"rescaleNewMax$band").toXML
      )

    val rgbOps = channels.flatMap { b =>
      clamp(b) :: normalize(b) :: rescale(b) :: Nil
    }

    val rgb: Elem = ExtendedElement("RGB", rgbOps: _*)

    ExtendedCapabilities(focalHillshade, focalSlope, rgb)
  }

  private val histoCache: Cache[OgcLayer, Interpreted[List[Histogram[Double]]]] =
    Scaffeine()
      .recordStats()
      .expireAfterWrite(1.hour)
      .maximumSize(500)
      .build[OgcLayer, Interpreted[List[Histogram[Double]]]]()

  private val tileCache: Cache[GetMap, Array[Byte]] =
    Scaffeine()
      .recordStats()
      .expireAfterWrite(1.hour)
      .maximumSize(500)
      .build[GetMap, Array[Byte]]()

  def responseFor(req: Request[F]): F[Response[F]] = {
    WmsParams(req.multiParams) match {
      case Invalid(errors) =>
        val msg = ParamError.generateErrorMessage(errors.toList)
        logger.warn(msg) *> BadRequest(msg)

      case Valid(_: GetCapabilities) =>
        logger.debug(ansi"%bold{GetCapabilities: ${req.uri}}") *>
          new CapabilitiesView[F](wmsModel, serviceUrl, extendedCapabilities).toXML flatMap {
            Ok(_)
          }

      case Valid(wmsReq: GetMap) =>
        logger.debug(ansi"%bold{GetMap: ${req.uri}}") *> {
          val re  = RasterExtent(wmsReq.boundingBox, wmsReq.width, wmsReq.height)
          val res = wmsModel
            .getLayer(wmsReq)
            .flatMap { layers =>
              layers
                .map { layer =>
                  val evalExtent = layer match {
                    case sl @ SimpleOgcLayer(_, _, _, _, _, _, _)               => LayerExtent.concurrent(sl)
                    case MapAlgebraOgcLayer(_, _, _, parameters, expr, _, _, _) =>
                      LayerExtent(expr.pure[F], parameters.pure[F], ConcurrentInterpreter.DEFAULT[F])
                  }

                  val evalHisto = layer match {
                    case sl @ SimpleOgcLayer(_, _, _, _, _, _, _)               => LayerHistogram.concurrent(sl, 512)
                    case MapAlgebraOgcLayer(_, _, _, parameters, expr, _, _, _) =>
                      LayerHistogram(expr.pure[F], parameters.pure[F], ConcurrentInterpreter.DEFAULT[F], 512)
                  }

                  // TODO: remove this once GeoTiffRasterSource would be threadsafe
                  // ETA 6/22/2020: we're pretending everything is fine
                  val histIO = for {
                    cached <- Sync[F].delay { histoCache.getIfPresent(layer) }
                    hist   <- cached match {
                                case Some(h) => h.pure[F]
                                case None    => evalHisto
                              }
                    _      <- Sync[F].delay { histoCache.put(layer, hist) }
                  } yield hist

                  (evalExtent(re.extent, re.cellSize.some), histIO).parMapN {
                    case (Valid(mbtile), Valid(hists)) => Valid((mbtile, hists))
                    case (Invalid(errs), _)            => Invalid(errs)
                    case (_, Invalid(errs))            => Invalid(errs)
                  }.attempt flatMap {
                    case Right(Valid((mbtile, hists))) => // success
                      val rendered = Raster(mbtile, re.extent).render(wmsReq.crs, layer.style, wmsReq.format, hists)
                      tileCache.put(wmsReq, rendered)
                      Ok(rendered).map(_.putHeaders(`Content-Type`(ToMediaType(wmsReq.format))))
                    case Right(Invalid(errs))          => // maml-specific errors
                      logger.debug(errs.toList.toString)
                      BadRequest(errs.asJson)
                    case Left(err)                     => // exceptions
                      logger.error(err.stackTraceString)
                      InternalServerError(err.stackTraceString)
                  }
                }
                .headOption
                .getOrElse(wmsReq.layers.headOption match {
                  case Some(layerName) =>
                    // Handle the case where the STAC item was requested for some area between the tiles.
                    // STAC search will return an empty list, however QGIS may expect a test pixel to
                    // return the actual tile
                    // TODO: is there a better way to handle it?
                    wmsModel.sources
                      .find(withName(layerName))
                      .flatMap {
                        _.headOption match {
                          case Some(_) =>
                            val tile   = ArrayTile(Array(0, 0), 1, 1)
                            val raster = Raster(MultibandTile(tile, tile, tile), wmsReq.boundingBox)
                            Ok(raster.render(wmsReq.crs, None, wmsReq.format, Nil)).map(_.putHeaders(`Content-Type`(ToMediaType(wmsReq.format))))
                          case _       => BadRequest(s"Layer ($layerName) not found or CRS (${wmsReq.crs}) not supported")
                        }
                      }
                  case None            => BadRequest(s"Layer not found (no layer name provided in request)")
                })
            }

          tileCache.getIfPresent(wmsReq) match {
            case Some(rendered) => Ok(rendered).map(_.putHeaders(`Content-Type`(ToMediaType(wmsReq.format))))
            case _              => res
          }
        }
    }
  }
}
