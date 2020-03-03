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

import geotrellis.server.stac.Implicits._

import io.circe._
import io.circe.shapes.CoproductInstances
import io.circe.generic.semiauto._
import io.circe.syntax._
import shapeless._

import java.time.Instant

case class SpatialExtent(bbox: List[Bbox])
object SpatialExtent {
  implicit val encSpatialExtent: Encoder[SpatialExtent] = deriveEncoder
  implicit val decSpatialExtent: Decoder[SpatialExtent] = deriveDecoder
}

case class Interval(interval: List[TemporalExtent])
object Interval {
  implicit val encInterval: Encoder[Interval] = deriveEncoder
  implicit val decInterval: Decoder[Interval] = deriveDecoder
}

case class StacExtent(
    spatial: SpatialExtent,
    temporal: Interval
)

object StacExtent {
  implicit val encStacExtent: Encoder[StacExtent] = deriveEncoder
  implicit val decStacExtent: Decoder[StacExtent] = deriveDecoder
}
