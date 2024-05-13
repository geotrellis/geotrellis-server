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

import scala.xml.NamespaceBinding

package object wcs {
  val wcsScope: NamespaceBinding = scalaxb.toScope(
    None -> "http://www.opengis.net/wcs/1.1.1",
    Some("gml") -> "http://www.opengis.net/gml",
    Some("ows") -> "http://www.opengis.net/ows/1.1",
    Some("ogc") -> "http://www.opengis.net/ogc",
    Some("xlink") -> "http://www.w3.org/1999/xlink",
    Some("xsi") -> "http://www.w3.org/2001/XMLSchema-instance"
  )
}
