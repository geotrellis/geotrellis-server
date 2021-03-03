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

package geotrellis.server.ogc.wmts

import geotrellis.store.query._
import geotrellis.server.ogc.OutputFormat
import geotrellis.server.ogc.params._

import cats.syntax.apply._
import cats.data.{Validated, ValidatedNel}
import Validated._

import scala.util.Try

abstract sealed class WmtsParams {
  val version: String
}

object WmtsParams {
  lazy val logger = org.log4s.getLogger

  val wmtsVersion = "1.0.0"

  final case class GetCapabilities(
    version: String,
    format: Option[String],
    updateSequence: Option[String]
  ) extends WmtsParams

  object GetCapabilities {
    def build(params: ParamMap): ValidatedNel[ParamError, WmtsParams] =
      (params.validatedVersion(wmtsVersion), params.validatedOptionalParam("format"), params.validatedOptionalParam("updatesequence"))
        .mapN(GetCapabilities.apply)
  }

  case class GetTile(
    version: String,
    layer: String,
    style: String,
    format: OutputFormat,
    tileMatrixSet: String,
    tileMatrix: String,
    tileRow: Int,
    tileCol: Int
  ) extends WmtsParams {
    def toQuery: Query = withName(layer)
  }

  object GetTile {
    def build(params: ParamMap): ValidatedNel[ParamError, WmtsParams] = {
      logger.trace(s"PARAM MAP: ${params.params}")
      val versionParam = params.validatedVersion(wmtsVersion)

      versionParam
        .andThen { version: String =>
          val layer         = params.validatedParam("layer")
          val style         = params.validatedParam("style")
          val tileMatrixSet = params.validatedParam("tilematrixset")
          val tileMatrix    = params.validatedParam("tilematrix")
          val tileRow       = params.validatedParam[Int]("tilerow", { s => Try(s.toInt).toOption })
          val tileCol       = params.validatedParam[Int]("tilecol", { s => Try(s.toInt).toOption })

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

          (layer, style, tileMatrixSet, tileMatrix, format, tileRow, tileCol).mapN {
            case (layer, style, tileMatrixSet, tileMatrix, format, tileRow, tileCol) =>
              GetTile(version, layer, style, format, tileMatrixSet, tileMatrix, tileRow, tileCol)
          }
        }
    }
  }

  def apply(queryParams: Map[String, Seq[String]]): ValidatedNel[ParamError, WmtsParams] = {
    val params       = ParamMap(queryParams)
    val serviceParam = params.validatedParam("service", validValues = Set("wmts"))
    val requestParam = params.validatedParam("request", validValues = Set("getcapabilities", "gettile"))

    val firstStageValidation = (serviceParam, requestParam).mapN { case (_, b) => b }

    firstStageValidation.andThen {
      case "getcapabilities" => GetCapabilities.build(params)
      case "gettile"         => GetTile.build(params)
    }
  }
}
