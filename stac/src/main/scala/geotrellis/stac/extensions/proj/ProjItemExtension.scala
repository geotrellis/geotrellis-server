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

package geotrellis.stac.extensions.proj

import geotrellis.stac._

import geotrellis.vector.{io => _, _}
import com.azavea.stac4s.extensions.{ItemExtension, StacAssetExtension}
import cats.kernel.Eq
import io.circe.generic.extras.{ConfiguredJsonCodec, JsonKey}

@ConfiguredJsonCodec
case class ProjItemExtension(
  @JsonKey("proj:epsg") epsgCode: Option[Int],
  @JsonKey("proj:wkt2") wktString: Option[String],
  @JsonKey("proj:geometry") geometry: Option[Geometry],
  @JsonKey("proj:transform") transform: Option[ProjTransform],
  @JsonKey("proj:shape") shape: Option[ProjShape]
)

object ProjItemExtension {
  implicit val eq: Eq[ProjItemExtension] = Eq.fromUniversalEquals

  implicit lazy val itemExtension: ItemExtension[ProjItemExtension] = ItemExtension.instance
  implicit lazy val stacAssetExtension: StacAssetExtension[ProjItemExtension] = StacAssetExtension.instance
}
