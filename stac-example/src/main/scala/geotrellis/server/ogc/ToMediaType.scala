/*
 * Copyright 2021 Azavea
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

import org.http4s.MediaType

object ToMediaType {
  def apply(format: OutputFormat): MediaType = format match {
    case OutputFormat.Png(_)  => MediaType.image.png
    case OutputFormat.Jpg     => MediaType.image.jpeg
    case OutputFormat.GeoTiff => MediaType.image.tiff
  }

  def apply(format: InfoFormat): MediaType = format match {
    case InfoFormat.Json => MediaType.application.json
    case InfoFormat.XML  => MediaType.text.xml
  }
}
