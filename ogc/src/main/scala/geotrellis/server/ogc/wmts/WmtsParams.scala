package geotrellis.server.ogc.wmts

import geotrellis.server.ogc.OutputFormat
import geotrellis.server.ogc.params._

import cats.implicits._
import cats.data.{Validated, ValidatedNel}
import Validated._
import com.typesafe.scalalogging.LazyLogging

import scala.util.Try

abstract sealed class WmtsParams {
  val version: String
}

object WmtsParams extends LazyLogging {
  final case class GetCapabilities(
    version: String,
    format: Option[String],
    updateSequence: Option[String]
  ) extends WmtsParams

  object GetCapabilities {
    def build(params: ParamMap): ValidatedNel[ParamError, WmtsParams] = {
      (params.validatedVersion("1.0.0"),
        params.validatedOptionalParam("format"),
        params.validatedOptionalParam("updatesequence")
      ).mapN(GetCapabilities.apply)
    }
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
  ) extends WmtsParams

  object GetTile {
    def build(params: ParamMap): ValidatedNel[ParamError, WmtsParams] = {
      logger.debug(s"PARAM MAP: ${params.params}")
      val versionParam =
        params.validatedVersion("1.0.0")

      versionParam
        .andThen { version: String =>
          val layer = params.validatedParam("layer")

          val style = params.validatedParam("style")

          val tileMatrixSet = params.validatedParam("tilematrixset")

          val tileMatrix = params.validatedParam("tilematrix")

          val tileRow =
            params.validatedParam[Int]("tilerow", { s => Try(s.toInt).toOption })

          val tileCol =
            params.validatedParam[Int]("tilecol", { s => Try(s.toInt).toOption })

          val format =
            params.validatedParam("format")
              .andThen { f =>
                OutputFormat.fromString(f) match {
                  case Some(format) => Valid(format).toValidatedNel
                  case None =>
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
    val params = new ParamMap(queryParams)

    val serviceParam =
      params.validatedParam("service", validValues=Set("wmts"))

    val requestParam =
      params.validatedParam("request", validValues=Set("getcapabilities", "gettile"))

    val firstStageValidation =
      (serviceParam, requestParam).mapN { case (a, b) => b }

    firstStageValidation.andThen {
      case "getcapabilities" =>
        GetCapabilities.build(params)
      case "gettile" =>
        GetTile.build(params)
    }
  }
}
