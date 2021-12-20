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

import geotrellis.server.ogc._
import geotrellis.server.ogc.utils._
import geotrellis.server.ogc.params.ParamError
import geotrellis.server.ogc.wms.WmsParams.{GetCapabilitiesParams, GetFeatureInfoExtendedParams, GetFeatureInfoParams, GetMapParams}
import geotrellis.vector.{io => _, Extent}
import geotrellis.raster.{io => _, _}
import com.azavea.maml.error._
import org.http4s.scalaxml._
import org.http4s._
import org.http4s.circe._
import org.http4s.dsl.Http4sDsl
import io.circe.syntax._
import cats.effect._
import cats.Parallel
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.syntax.option._
import cats.data.Validated._
import io.chrisdavenport.log4cats.Logger
import com.github.blemale.scaffeine.{Cache, Scaffeine}
import org.backuity.ansi.AnsiFormatter.FormattedHelper
import opengis._
import org.http4s.circe.jsonOf
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

    val channels = "Red" :: "Green" :: "Blue" :: Nil
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

  private lazy val histoCache: Cache[OgcLayer, Interpreted[List[Histogram[Double]]]] =
    Scaffeine()
      .recordStats()
      .expireAfterWrite(1.hour)
      .maximumSize(500)
      .build[OgcLayer, Interpreted[List[Histogram[Double]]]]()

  private lazy val tileCache: Cache[GetMapParams, Array[Byte]] =
    Scaffeine()
      .recordStats()
      .expireAfterWrite(1.hour)
      .maximumSize(500)
      .build[GetMapParams, Array[Byte]]()

  private lazy val rasterCache: Cache[GetMapParams, Raster[MultibandTile]] =
    Scaffeine()
      .recordStats()
      .expireAfterWrite(1.hour)
      .maximumSize(500)
      .build[GetMapParams, Raster[MultibandTile]]()

  private lazy val rasterCacheFeatureInfoExtended: Cache[(String, Extent), MultibandTile] =
    Scaffeine()
      .recordStats()
      .expireAfterWrite(1.hour)
      .maximumSize(500)
      .build[(String, Extent), MultibandTile]()

  implicit val getFeatureInfoExtendedParamsDecoder = jsonOf[F, GetFeatureInfoExtendedParams]

  def responseFor(req: Request[F]): F[Response[F]] =
    WmsParams(req.multiParams) match {
      case Invalid(errors) =>
        val msg = ParamError.generateErrorMessage(errors.toList)
        logger.warn(msg) >> BadRequest(msg)

      case Valid(_: GetCapabilitiesParams) =>
        logger.debug(ansi"%bold{GetCapabilities: ${req.uri}}") >>
          CapabilitiesView[F](wmsModel, serviceUrl, extendedCapabilities).toXML.flatMap(Ok(_))

      case Valid(wmsReq: GetMapParams) =>
        logger.debug(ansi"%bold{GetMap: ${req.uri}}") >>
          GetMap(wmsModel, tileCache, histoCache)
            .build(wmsReq)
            .flatMap {
              case Right(rendered)                      => Ok(rendered).map(_.putHeaders(`Content-Type`(ToMediaType(wmsReq.format))))
              case Left(GetMapBadRequest(msg))          => BadRequest(msg)
              case Left(GetMapInternalServerError(msg)) => InternalServerError(msg)
            }

      case Valid(wmsReq: GetFeatureInfoParams) =>
        logger.debug(ansi"%bold{GetFeatureInfo: ${req.uri}}") >>
          GetFeatureInfo[F](wmsModel, rasterCache)
            .build(wmsReq)
            .flatMap {
              case Right(f) =>
                Ok(f.render(wmsReq.infoFormat, wmsReq.crs, wmsReq.cellSize)).map(_.putHeaders(`Content-Type`(ToMediaType(wmsReq.infoFormat))))
              case Left(e: LayerNotDefinedException) =>
                NotFound(e.render(wmsReq.infoFormat)).map(_.putHeaders(`Content-Type`(ToMediaType(wmsReq.infoFormat))))
              case Left(e: InvalidPointException) =>
                BadRequest(e.render(wmsReq.infoFormat)).map(_.putHeaders(`Content-Type`(ToMediaType(wmsReq.infoFormat))))
            }
    }

  def getFetureInfoExtended(req: Request[F]): F[Response[F]] =
    logger.debug(ansi"%bold{GetFeatureInfoExtended: ${req.uri}}") >>
      req.as[GetFeatureInfoExtendedParams].flatMap { params =>
        GetFeatureInfoExtended[F](wmsModel, rasterCacheFeatureInfoExtended)
          .build(params)
          .flatMap {
            case Right(f) => Ok(f.asJson)
            case Left(e: LayerNotDefinedException) =>
              NotFound(e.render(InfoFormat.Json)).map(_.putHeaders(`Content-Type`(ToMediaType(InfoFormat.Json))))
            case Left(e: InvalidPointException) =>
              BadRequest(e.render(InfoFormat.Json)).map(_.putHeaders(`Content-Type`(ToMediaType(InfoFormat.Json))))
          }
      }
}
