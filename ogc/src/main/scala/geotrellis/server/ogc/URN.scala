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

import geotrellis.proj4.CRS

object URN {
  def fromCrs(crs: CRS): Option[String] = crs.epsgCode.map { code => s"urn:ogc:def:crs:EPSG::$code" }
  def unsafeFromCrs(crs: CRS): String   =
    fromCrs(crs).getOrElse(throw new Exception(s"Unrecognized CRS: $crs. Unable to construct URN"))
}
