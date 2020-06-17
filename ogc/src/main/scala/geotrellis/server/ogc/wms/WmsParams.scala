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

import geotrellis.server.ogc.{OgcTime, OgcTimeEmpty, OgcTimeInterval, OgcTimePositions, OutputFormat}
import geotrellis.server.ogc.params._
import geotrellis.proj4.LatLng
import geotrellis.proj4.CRS
import geotrellis.store.query._
import geotrellis.vector.{Extent, ProjectedExtent}
import cats.implicits._
import cats.data.{Validated, ValidatedNel, NonEmptyList => NEL}
import Validated._
import jp.ne.opt.chronoscala.Imports._

import scala.util.Try

abstract sealed class WmsParams {
  val version: String
}

object WmsParams {
  final case class GetCapabilities(
    version: String,
    format: Option[String],
    updateSequence: Option[String]
  ) extends WmsParams

  object GetCapabilities {
    def build(params: ParamMap): ValidatedNel[ParamError, WmsParams] = {
      (params.validatedVersion("1.3.0"),
        params.validatedOptionalParam("format"),
        params.validatedOptionalParam("updatesequence")
      ).mapN(GetCapabilities.apply)
    }
  }

  case class GetMap(
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
        case timeInterval: OgcTimeInterval =>
          timeInterval.end match {
            case Some(end) => query and between(timeInterval.start, end)
            case None => query and at(timeInterval.start)
          }
        case OgcTimePositions(list) =>
          list match {
            case NEL(head, Nil) => query and at(head)
            case _ => query and list.toList.map(at(_)).reduce(_ or _)
          }
        case OgcTimeEmpty => query
      }
    }
  }

  object GetMap {
    def build(params: ParamMap): ValidatedNel[ParamError, WmsParams] = {
      val versionParam =
        params.validatedVersion("1.3.0")

      versionParam
        .andThen { version: String =>
          val layers =
            params.validatedParam[List[String]]("layers", { s => Some(s.split(",").toList) })

          val styles: ValidatedNel[ParamError, List[String]] =
            params.validatedParam[List[String]]("styles", { s => Some(s.split(",").toList) })

          val crs = params.validatedParam("crs", { s => Try(CRS.fromName(s)).toOption })

          val bbox = crs.andThen { crs =>
            params.validatedParam("bbox", {s =>
              s.split(",").map(_.toDouble) match {
                case Array(xmin, ymin, xmax, ymax) =>
                  if (crs == LatLng) Some(Extent(ymin, xmin, ymax, xmax))
                  else  Some(Extent(xmin, ymin, xmax, ymax))
                case _ => None
              }
            })
          }

          val width =
            params.validatedParam[Int]("width", { s => Try(s.toInt).toOption })

          val height =
            params.validatedParam[Int]("height", { s => Try(s.toInt).toOption })

          val time = params.validatedOgcTime("time")

          val format =
            params
              .validatedParam("format")
              .andThen { f =>
                OutputFormat.fromString(f) match {
                  case Some(format) => Valid(format).toValidatedNel
                  case None =>
                    Invalid(ParamError.UnsupportedFormatError(f)).toValidatedNel
                  }
              }

          (layers, styles, bbox, format, width, height, crs, time).mapN {
            case (layers, styles, bbox, format, width, height, crs, time) =>
              GetMap(version, layers, styles, bbox, format = format, width = width, height = height, crs = crs, time = time, params)
          }
        }
    }
  }

  def apply(queryParams: Map[String, Seq[String]]): ValidatedNel[ParamError, WmsParams] = {
    val params = ParamMap(queryParams)

    val serviceParam =
      params.validatedParam("service", validValues = Set("wms"))

    val requestParam =
      params.validatedParam("request", validValues = Set("getcapabilities", "getmap"))

    val firstStageValidation =
      (serviceParam, requestParam).mapN { case (_, b) => b }

    firstStageValidation.andThen {
      case "getcapabilities" =>
        GetCapabilities.build(params)
      case "getmap" =>
        GetMap.build(params)
    }
  }
}
