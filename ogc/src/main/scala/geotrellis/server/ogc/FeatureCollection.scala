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

import geotrellis.vector.io.json.GeometryFormats
import geotrellis.vector.{Feature, Geometry}
import io.circe.{Encoder, Json}
import io.circe.syntax._

/**
 * TODO: consider moving it into GeoTrellis
 */
case class FeatureCollection[G <: Geometry, D](list: List[Feature[G, D]])

object FeatureCollection {
  private def writeFeatureCollectionJson[G <: Geometry, D: Encoder](obj: Feature[G, D]): Json =
    Json.obj(
      "type" -> "Feature".asJson,
      "geometry" -> GeometryFormats.geometryEncoder(obj.geom),
      "properties" -> obj.data.asJson
    )

  implicit def featureCollectionEncoder[G <: Geometry, D: Encoder]: Encoder[FeatureCollection[G, D]] =
    Encoder.encodeJson.contramap { fc =>
      Json.obj(
        "type" -> "FeatureCollection".asJson,
        "features" -> fc.list.map(writeFeatureCollectionJson[G, D]).asJson
      )
    }
}
