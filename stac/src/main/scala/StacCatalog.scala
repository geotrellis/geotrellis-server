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

package geotrellis.server.stac

import io.circe._

case class StacCatalog(
    stacVersion: String,
    id: String,
    title: Option[String],
    description: String,
    links: List[StacLink]
)

object StacCatalog {
  implicit val encCatalog: Encoder[StacCatalog] =
    Encoder.forProduct5("stac_version", "id", "title", "description", "links")(
      catalog =>
        (
          catalog.stacVersion,
          catalog.id,
          catalog.title,
          catalog.description,
          catalog.links
        )
    )

  implicit val decCatalog: Decoder[StacCatalog] =
    Decoder.forProduct5("stac_version", "id", "title", "description", "links")(
      StacCatalog.apply _)
}
