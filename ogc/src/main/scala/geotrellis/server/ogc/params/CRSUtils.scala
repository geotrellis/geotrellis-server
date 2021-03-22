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

package geotrellis.server.ogc.params

import geotrellis.proj4._
import cats.data.{Validated, ValidatedNel}
import Validated._

import scala.util.{Success, Try}

object CRSUtils {

  /** Converts an OGC URN string representing a CRS into a [[geotrellis.proj4.CRS]] instance.
    * TODO: Move this into geotrellis.proj4 (or future geotrellis.crs)
    */
  def ogcToCRS(crsDesc: String): ValidatedNel[ParamError, CRS] = {
    val code = crsDesc.trim.toLowerCase
    if (code == "wgs84(dd)") Valid(LatLng).toValidatedNel
    else if (code.startsWith("urn:ogc:def:crs:epsg::"))
      Try(CRS.fromEpsgCode(code.split("::")(1).toInt)) match {
        case Success(crs) => Valid(crs).toValidatedNel
        case _            => Invalid(ParamError.CrsParseError(crsDesc)).toValidatedNel
      }
    else Valid(LatLng).toValidatedNel // TODO: Complete implementation WCS codes (urn:ogc:*) -> CRS
  }
}
