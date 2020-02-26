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

package geotrellis.server.ogc

import java.time.{ZoneId, ZonedDateTime}
import java.time.format.DateTimeFormatter

import geotrellis.vector.Extent

import scala.xml.NamespaceBinding

package object wcs {
  val wcsScope: NamespaceBinding = scalaxb.toScope(
    Some("gml") -> "http://www.opengis.net/gml",
    Some("ows") -> "http://www.opengis.net/ows/1.1",
    Some("ogc") -> "http://www.opengis.net/ogc",
    Some("wcs") -> "http://www.opengis.net/wcs/1.1.1",
    Some("xlink") -> "http://www.w3.org/1999/xlink",
    Some("xsi") -> "http://www.w3.org/2001/XMLSchema-instance"
  )

  val constrainedWCSScope: NamespaceBinding = scalaxb.toScope(
    Some("gml") -> "http://www.opengis.net/gml",
    Some("ows") -> "http://www.opengis.net/ows/1.1",
    Some("ogc") -> "http://www.opengis.net/ogc",
    Some("xlink") -> "http://www.w3.org/1999/xlink",
    Some("xsi") -> "http://www.w3.org/2001/XMLSchema-instance"
  )

  implicit class ExtentOps(self: Extent) {
    def swapXY: Extent = Extent(
      xmin = self.ymin,
      ymin = self.xmin,
      xmax = self.ymax,
      ymax = self.xmax
    )
  }

  /**
   * WCS expects this very specific format for its time strings, which is not quite (TM)
   * what Java's toString method returns. Instead we convert to Instant.toString, which
   * does conform.
   *
   * The [ISO 8601:2000] syntax for dates and times may be summarized by the following template
   * (see Annex D of the OGC Web Map Service [OGC 06-042]):
   * ccyy-mm-ddThh:mm:ss.sssZ
   * Where
   * ― ccyy-mm-dd is the date (a four-digit year, and a two-digit month and day);
   * ― Tis a separator between the data and time strings;
   * ― hh:mm:ss.sss is the time (a two-digit hour and minute, and fractional seconds);
   * ― Z represents the Coordinated Universal Time (UTC or ―zulu‖) time zone.
   *
   * This was excerpted from "WCS Implementation Standard 1.1" available at:
   * https://portal.ogc.org/files/07-067r5
   *
   * @param zdt
   */
  implicit class ZonedDateTimeWcsOps(val zdt: ZonedDateTime) {
    def toWcsIsoString: String = zdt.toInstant.toString
  }
}
