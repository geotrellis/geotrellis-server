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
  final case class GetCapabilities(version: String) extends WmsParams

  object GetCapabilities {
    def build(params: ParamMap): ValidatedNel[ParamError, WmsParams] = {
      val versionParam =
        params.validatedVersion("1.3.0")

      versionParam.map { version: String =>
        GetCapabilities(version)
      }
    }
  }

  case class DescribeCoverage(version: String, identifiers: Seq[String]) extends WmsParams

  object DescribeCoverage {
    def build(params: ParamMap): ValidatedNel[ParamError, WmsParams] = {
      val versionParam =
        params.validatedVersion("1.3.0")

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
          DescribeCoverage(version, identifiers)
        }
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

  /** Defines valid request types, and the WmsParams to build from them. */
  private val requestMap: Map[String, ParamMap => ValidatedNel[ParamError, WmsParams]] =
    Map(
      "getcapabilities" -> GetCapabilities.build _,
      "describecoverage" -> DescribeCoverage.build _,
      "getmap" -> GetMap.build _
    )

  private val validRequests = requestMap.keys.toSet

  def apply(queryParams: Map[String, Seq[String]]): ValidatedNel[ParamError, WmsParams] = {
    val params = ParamMap(queryParams)

    val serviceParam =
      params.validatedParam("service", validValues=Set("wms"))

    val requestParam =
      params.validatedParam("request", validValues=validRequests)

    val firstStageValidation =
      (serviceParam, requestParam).mapN { case (a, b) => b }

    firstStageValidation
      .andThen { request =>
        // Further validation and building based on request type.
        requestMap(request)(params)
      }
  }
}
