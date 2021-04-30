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
import geotrellis.server.ogc.params._
import geotrellis.proj4.CRS
import geotrellis.store.query._
import geotrellis.vector.{Extent, ProjectedExtent}
import cats.syntax.apply._
import cats.syntax.option._
import cats.data.{Validated, ValidatedNel}
import Validated._
import geotrellis.raster.{CellSize, RasterExtent}

import scala.util.Try

abstract sealed class WmsParams {
  val version: String
}

object WmsParams {

  val wmsVersion = "1.3.0"

  final case class GetCapabilitiesParams(
    version: String,
    format: Option[String],
    updateSequence: Option[String]
  ) extends WmsParams

  object GetCapabilitiesParams {
    def build(params: ParamMap): ValidatedNel[ParamError, WmsParams] =
      (params.validatedVersion(wmsVersion), params.validatedOptionalParam("format"), params.validatedOptionalParam("updatesequence"))
        .mapN(GetCapabilitiesParams.apply)
  }

  case class GetMapParams(
    version: String,
    layers: List[String],
    styles: List[String],
    boundingBox: Extent,
    format: OutputFormat,
    width: Int,
    height: Int,
    crs: CRS,
    time: OgcTime,
    params: ParamMap
  ) extends WmsParams {
    def toQuery: Query = {
      val layer = layers.headOption.map(withName).getOrElse(nothing)
      val query = layer and intersects(ProjectedExtent(boundingBox, crs))
      time match {
        case timeInterval: OgcTimeInterval => query and between(timeInterval.start, timeInterval.end)
        case OgcTimePositions(list)        => query and list.toList.map(at(_)).reduce(_ or _)
        case OgcTimeEmpty                  => query
      }
    }

    def rasterExtent: RasterExtent = RasterExtent(boundingBox, width, height)

    def cellSize: CellSize = rasterExtent.cellSize
  }

  object GetMapParams {
    def build(params: ParamMap): ValidatedNel[ParamError, WmsParams] = {
      val versionParam = params.validatedVersion(wmsVersion)
      versionParam
        .andThen { version =>
          val layers = params.validatedParam[List[String]]("layers", { s => s.split(",").toList.some })
          val styles = params.validatedParam[List[String]]("styles", { s => s.split(",").toList.some })
          val crs    = params.validatedParam("crs", { s => Try(CRS.fromName(s)).toOption })

          val bbox = crs.andThen { crs =>
            params.validatedParam(
              "bbox",
              { s =>
                s.split(",").map(_.toDouble) match {
                  case Array(xmin, ymin, xmax, ymax) =>
                    val extent = Extent(xmin, ymin, xmax, ymax)
                    if (crs.isGeographic) extent.swapXY.some
                    else extent.some
                  case _                             => None
                }
              }
            )
          }

          val width  = params.validatedParam[Int]("width", { s => Try(s.toInt).toOption })
          val height = params.validatedParam[Int]("height", { s => Try(s.toInt).toOption })
          val time   = params.validatedOgcTime("time")

          val format =
            params
              .validatedParam("format")
              .andThen { f =>
                OutputFormat.fromString(f) match {
                  case Some(format) => Valid(format).toValidatedNel
                  case None         =>
                    Invalid(ParamError.UnsupportedFormatError(f)).toValidatedNel
                }
              }

          (layers, styles, bbox, format, width, height, crs, time).mapN { case (layers, styles, bbox, format, width, height, crs, time) =>
            GetMapParams(version, layers, styles, bbox, format = format, width = width, height = height, crs = crs, time = time, params)
          }
        }
    }
  }

  case class GetFeatureInfoParams(
    version: String,
    infoFormat: InfoFormat,
    queryLayers: List[String],
    i: Int, // col
    j: Int, // row
    exceptions: InfoFormat,
    // GetMap params
    layers: List[String],
    boundingBox: Extent,
    format: OutputFormat,
    width: Int,
    height: Int,
    crs: CRS,
    time: OgcTime,
    params: ParamMap
  ) extends WmsParams {
    def toGetMapParams: GetMapParams =
      GetMapParams(version, layers, Nil, boundingBox, format, width, height, crs, time, params)

    def toGetMapParamsQuery: GetMapParams =
      GetMapParams(version, layers.filter(queryLayers.contains), Nil, boundingBox, format, width, height, crs, time, params)

    def rasterExtent: RasterExtent = RasterExtent(boundingBox, width, height)

    def cellSize: CellSize = rasterExtent.cellSize
  }

  object GetFeatureInfoParams {
    def build(params: ParamMap): ValidatedNel[ParamError, WmsParams] = {

      val getMap: ValidatedNel[ParamError, WmsParams]    = GetMapParams.build(params)
      val versionParam: ValidatedNel[ParamError, String] = params.validatedVersion(wmsVersion)
      (versionParam, getMap).tupled
        .andThen {
          case (version, getMap: GetMapParams) =>
            val infoFormat =
              params
                .validatedParam("info_format")
                .andThen { f =>
                  InfoFormat.fromString(f) match {
                    case Some(format) => Valid(format).toValidatedNel
                    case None         => Valid(InfoFormat.XML).toValidatedNel
                  }
                }

            val exceptions =
              params
                .validatedOptionalParam("exceptions")
                .andThen {
                  case Some(f) =>
                    InfoFormat.fromString(f) match {
                      case Some(format) => Valid(format).toValidatedNel
                      case None         => Valid(InfoFormat.XML).toValidatedNel
                    }
                  case _       => Valid(InfoFormat.XML).toValidatedNel
                }

            val queryLayers = params.validatedParam[List[String]]("query_layers", { s => s.split(",").toList.some })

            val i = params.validatedParam[Int]("i", { s => Try(s.toInt).toOption })
            val j = params.validatedParam[Int]("j", { s => Try(s.toInt).toOption })

            (infoFormat, exceptions, queryLayers, i, j).mapN { case (infoFormat, exceptions, queryLayers, i, j) =>
              GetFeatureInfoParams(
                version,
                infoFormat,
                queryLayers,
                i,
                j,
                exceptions,
                getMap.layers,
                getMap.boundingBox,
                getMap.format,
                getMap.width,
                getMap.height,
                getMap.crs,
                getMap.time,
                params
              )
            }
          case (_, getMap)                     => Invalid(ParamError.InvalidValue("getMap", getMap.toString, Nil)).toValidatedNel
        }
    }
  }

  def apply(queryParams: Map[String, Seq[String]]): ValidatedNel[ParamError, WmsParams] = {
    val params = ParamMap(queryParams)

    val serviceParam         = params.validatedParam("service", validValues = Set("wms"))
    val requestParam         = params.validatedParam("request", validValues = Set("getcapabilities", "getmap", "getfeatureinfo"))
    val firstStageValidation = (serviceParam, requestParam).mapN { case (_, b) => b }

    firstStageValidation.andThen {
      case "getcapabilities" => GetCapabilitiesParams.build(params)
      case "getmap"          => GetMapParams.build(params)
      case "getfeatureinfo"  => GetFeatureInfoParams.build(params)
    }
  }
}
