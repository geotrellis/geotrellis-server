package geotrellis.server.wcs.params

import geotrellis.server.wcs.Constants.SUPPORTED_FORMATS

import cats._
import cats.implicits._
import cats.data.{NonEmptyList, Validated, ValidatedNel}
import Validated._
import geotrellis.proj4.CRS
import geotrellis.vector.Extent

import scala.util.Try

abstract sealed class WCSParams

case class GetCapabilitiesWCSParams(version: String) extends WCSParams

case class DescribeCoverageWCSParams(version: String, identifiers: Seq[String]) extends WCSParams

case class GetCoverageWCSParams(
  version: String,
  identifier: String,
  boundingBox: Extent,
  format: String,
  width: Int,
  height: Int,
  crs: CRS
) extends WCSParams

// Companion objects below define parsing logic from parameter map to WCSParams //

object WCSParams {
  /** Defines valid request types, and the WCSParams to build from them. */
  private val requestMap: Map[String, ParamMap => ValidatedNel[WCSParamsError, WCSParams]] =
    Map(
      "getcapabilities" -> GetCapabilitiesWCSParams.build _,
      "describecoverage" -> DescribeCoverageWCSParams.build _,
      "getcoverage" -> GetCoverageWCSParams.build _
    )

  private val validRequests = requestMap.keys.toSet

  def apply(queryParams: Map[String, Seq[String]]): ValidatedNel[WCSParamsError, WCSParams] = {
    val params = ParamMap(queryParams)

    val serviceParam =
      params.validatedParam("service", validValues=Set("wcs"))

    val requestParam =
      params.validatedParam("request", validValues=validRequests)

    val firstStageValidation =
      Apply[ValidatedNel[WCSParamsError, ?]].map2(
        serviceParam,
        requestParam
      ) { case (a, b) => b }

    firstStageValidation
      .andThen { request =>
        // Further validation and building based on request type.
        requestMap(request)(params)
      }
  }
}

object GetCapabilitiesWCSParams {
  def build(params: ParamMap): ValidatedNel[WCSParamsError, WCSParams] = {
    val versionParam =
      params.validatedVersion

    versionParam.map { version: String =>
      GetCapabilitiesWCSParams(version)
    }
  }
}

object DescribeCoverageWCSParams {
  def build(params: ParamMap): ValidatedNel[WCSParamsError, WCSParams] = {
    val versionParam =
      params.validatedVersion

    versionParam
      .andThen { version: String =>
        // Version 1.1.0 switched from single "coverage" to multiple "identifiers"
        val identifiers =
          if(version < "1.1.0") {
            params.validatedParam("coverage").map(Seq(_))
          } else {
            params.validatedParam("identifiers").map(_.split(",").toSeq)
          }

        identifiers.map { ids => (version, ids) }
      }
      .map { case (version, identifiers) =>
        DescribeCoverageWCSParams(version, identifiers)
      }
  }
}

object GetCoverageWCSParams {

  private def getBboxAndCrsOption(params: ParamMap, field: String): ValidatedNel[WCSParamsError, (Vector[Double], Option[String])] =
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

  private[params] def build(params: ParamMap): ValidatedNel[WCSParamsError, WCSParams] = {
    val versionParam =
      params.validatedVersion

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

            Apply[ValidatedNel[WCSParamsError, ?]]
              .map2(identifier, bboxAndCrsOption) { case (id, (bbox, crsOption)) =>
                (id, bbox, crsOption)
              }
          } else {
            val identifier =
              params.validatedParam("identifier")

            val bboxAndCrsOption =
              getBboxAndCrsOption(params, "boundingbox")

            Apply[ValidatedNel[WCSParamsError, ?]]
              .map2(identifier, bboxAndCrsOption) { case (id, (bbox, crsOption)) =>
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
                  CRSUtils.ogcToCRS(crsDesc).map { crs => (id, bbox, crs) }
                case None =>
                  params.validatedParam("crs")
                    .andThen { crsDesc =>
                      CRSUtils.ogcToCRS(crsDesc).map { crs => (id, bbox, crs) }
                    }
              }
            }

        val format =
          params.validatedParam("format")
            .andThen { f =>
              SUPPORTED_FORMATS.get(f) match {
                case Some(format) => Valid(format).toValidatedNel
                case None =>
                  Invalid(UnsupportedFormatError(f)).toValidatedNel
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

      Apply[ValidatedNel[WCSParamsError, ?]]
        .map4(idAndBboxAndCrs, format, width, height) { case ((id, bbox, crs), format, width, height) =>
          val extent = Extent(bbox(0), bbox(1), bbox(2), bbox(3))
          GetCoverageWCSParams(version, id, extent, format, width, height, crs)
        }
      }
  }
}
