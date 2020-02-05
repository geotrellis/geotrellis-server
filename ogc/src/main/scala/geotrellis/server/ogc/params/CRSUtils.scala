package geotrellis.server.ogc.params

import geotrellis.proj4._
import cats.data.{Validated, ValidatedNel}
import Validated._

import scala.util.Try

object CRSUtils {
  /** Converts an OGC URN string representing a CRS into a [[geotrellis.proj4.CRS]] instance.
   * TODO: Move this into geotrellis.proj4 (or future geotrellis.crs)
   */
  def ogcToCRS(crsDesc: String): ValidatedNel[ParamError, CRS] = {
    val code = crsDesc.trim.toLowerCase
    if(code == "wgs84(dd)") {
      Valid(LatLng).toValidatedNel
    } else if(code.startsWith("urn:ogc:def:crs:epsg::")) {
      Try(
        CRS.fromEpsgCode(code.split("::")(1).toInt)
      ).toOption match {
        case Some(crs) => Valid(crs).toValidatedNel
        case None => Invalid(ParamError.CrsParseError(crsDesc)).toValidatedNel
      }
    } else {
      // TODO: Complete implementation WCS codes (urn:ogc:*) -> CRS
      Valid(LatLng).toValidatedNel
    }
  }
}
