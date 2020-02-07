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

case class ItemCollection(
    _type: String = "FeatureCollection",
    features: List[StacItem],
    links: List[StacLink]
)

object ItemCollection {
  implicit val encItemCollection: Encoder[ItemCollection] = Encoder.forProduct3(
    "type",
    "features",
    "links"
  )(
    itemCollection =>
      (
        itemCollection._type,
        itemCollection.features,
        itemCollection.links
      )
  )

  implicit val decItemCollection: Decoder[ItemCollection] = Decoder.forProduct3(
    "type",
    "features",
    "links"
  )(ItemCollection.apply _)
}
