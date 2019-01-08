package geotrellis.server.ogc.wms

import geotrellis.server.ogc.wms.Constants.SUPPORTED_FORMATS
import geotrellis.server.ogc.params._

import cats._
import cats.implicits._
import cats.data.{NonEmptyList, Validated, ValidatedNel}
import Validated._
import geotrellis.proj4.CRS
import geotrellis.vector.Extent

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
    identifier: String,
    boundingBox: Extent,
    format: String,
    width: Int,
    height: Int,
    crs: CRS
  ) extends WmsParams

  object GetMap {
    private def getBboxAndCrsOption(params: ParamMap, field: String): ValidatedNel[ParamError, (Vector[Double], Option[String])] =
      params.validatedParam[(Vector[Double], Option[String])](field, { bboxStr =>
        // Sometimes the CRS was a 5th element in the bbox param.
        try {
          val v = bboxStr.split(",").toVector
          if(v.length == 4) {
            Some((v.map(_.toDouble), None))
          } else if(v.length == 5) {
            val values = v
            Some((v.take(4).map(_.toDouble), Some(v.last)))
          } else {
            None
          }
        } catch {
          case _: Throwable => None
        }
      })

    def build(params: ParamMap): ValidatedNel[ParamError, WmsParams] = {
      val versionParam =
        params.validatedVersion("1.3.0")

      versionParam
        .andThen { version: String =>
          // Collected the bbox, id, and possibly the CRS in one shot.
          // This is beause the boundingbox param could contain the CRS as a 5th element.
          val idAndBboxAndCrsOption =
            if(version < "1.1.0") {
              val identifier =
                params.validatedParam("coverage")

              val bboxAndCrsOption =
                getBboxAndCrsOption(params, "bbox")

              (identifier, bboxAndCrsOption).mapN { case (id, (bbox, crsOption)) =>
                (id, bbox, crsOption)
              }
            } else {
              val identifier =
                params.validatedParam("identifier")

              val bboxAndCrsOption =
                getBboxAndCrsOption(params, "boundingbox")

              (identifier, bboxAndCrsOption).mapN { case (id, (bbox, crsOption)) =>
                (id, bbox, crsOption)
              }
            }

          // Transform the OGC urn CRS code into a CRS.
          val idAndBboxAndCrs =
            idAndBboxAndCrsOption
              .andThen { case (id, bbox, crsOption) =>
                // If the CRS wasn't in the boundingbox parameter, pull it out of the CRS field.
                crsOption match {
                  case Some(crsDesc) =>
                    CrsUtils.ogcToCRS(crsDesc).map { crs => (id, bbox, crs) }
                  case None =>
                    params.validatedParam("crs")
                      .andThen { crsDesc =>
                        CrsUtils.ogcToCRS(crsDesc).map { crs => (id, bbox, crs) }
                      }
                }
              }

          val format =
            params.validatedParam("format")
              .andThen { f =>
                SUPPORTED_FORMATS.get(f) match {
                  case Some(format) => Valid(format).toValidatedNel
                  case None =>
                    Invalid(ParamError.UnsupportedFormatError(f)).toValidatedNel
                  }
              }

          val width =
            params.validatedParam[Int]("width", { s =>
              Try(s.toInt).toOption
            })

          val height =
            params.validatedParam[Int]("height", { s =>
              Try(s.toInt).toOption
            })

          (idAndBboxAndCrs, format, width, height).mapN { case ((id, bbox, crs), format, width, height) =>
            val extent = Extent(bbox(0), bbox(1), bbox(2), bbox(3))
            GetMap(version, id, extent, format, width, height, crs)
          }
        }
    }
  }

  def apply(queryParams: Map[String, Seq[String]]): ValidatedNel[ParamError, WmsParams] = {
    val params = new ParamMap(queryParams)

    val serviceParam =
      params.validatedParam("service", validValues=Set("wms"))

    val requestParam =
      params.validatedParam("request", validValues=Set("getcapabilities", "getmap"))

    val firstStageValidation =
      (serviceParam, requestParam).mapN { case (a, b) => b }

    firstStageValidation.andThen {
      case "getcapabilities" =>
        GetCapabilities.build(params)
      case "getmap" =>
        GetMap.build(params)
    }
  }
}
