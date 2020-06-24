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

package geotrellis.server.vlm.geotiff

import geotrellis.server._
import geotrellis.server.vlm._
import geotrellis.layer._
import geotrellis.proj4.WebMercator
import geotrellis.raster._
import geotrellis.raster.resample.ResampleMethod
import geotrellis.raster.geotiff.GeoTiffRasterSource
import geotrellis.raster.io.geotiff.OverviewStrategy
import geotrellis.vector.Extent

import _root_.io.circe._
import _root_.io.circe.generic.semiauto._
import cats.data.NonEmptyList
import cats.effect._
import cats.syntax.functor._
import cats.data.{NonEmptyList => NEL}

import java.net.URI

case class GeoTiffNode(
  uri: URI,
  band: Int,
  celltype: Option[CellType],
  resampleMethod: ResampleMethod = ResampleMethod.DEFAULT,
  overviewStrategy: OverviewStrategy = OverviewStrategy.DEFAULT
)

object GeoTiffNode extends RasterSourceUtils {
  implicit val cogNodeEncoder: Encoder[GeoTiffNode] = deriveEncoder[GeoTiffNode]
  implicit val cogNodeDecoder: Decoder[GeoTiffNode] = deriveDecoder[GeoTiffNode]

  implicit def cogNodeRasterExtents[F[_]: Sync]: HasRasterExtents[F, GeoTiffNode] = { self =>
    Sync[F].delay {
      val rs = RasterSource(s"${self.uri}")
      NonEmptyList.fromListUnsafe(rs.resolutions map {
        RasterExtent(rs.extent, _)
      })
    }
  }

  implicit def cogNodeTmsReification[F[_]: Sync]: TmsReification[F, GeoTiffNode] =
    new TmsReification[F, GeoTiffNode] {
      val targetCRS = WebMercator

      val tmsLevels: Array[LayoutDefinition] = {
        val scheme = ZoomedLayoutScheme(targetCRS, 256)
        for (zoom <- 0 to 64) yield scheme.levelForZoom(zoom).layout
      }.toArray

      def tmsReification(
        self: GeoTiffNode,
        buffer: Int
      ): (Int, Int, Int) => F[ProjectedRaster[MultibandTile]] =
        (z: Int, x: Int, y: Int) =>
          Sync[F].delay {
            val layout = tmsLevels(z)
            val key    = SpatialKey(x, y)
            val raster = Raster(
              RasterSource(self.uri.toString)
                .reproject(targetCRS)
                .tileToLayout(
                  layout,
                  identity,
                  self.resampleMethod,
                  self.overviewStrategy
                )
                .read(key)
                .getOrElse(
                  throw new Exception(
                    s"Unable to retrieve layer $self at XY of ($x, $y)"
                  )
                ),
              layout.mapTransform(key)
            )

            ProjectedRaster(raster, targetCRS)
          }
    }

  implicit def CogNodeExtentReification[F[_]: Sync]: ExtentReification[F, GeoTiffNode] = { self => (extent: Extent, cs: CellSize) =>
    Sync[F].delay {
      RasterSource(self.uri.toString)
        .resample(
          TargetRegion(new GridExtent[Long](extent, cs)),
          self.resampleMethod,
          self.overviewStrategy
        )
        .read(extent, self.band :: Nil)
        .map(ProjectedRaster(_, WebMercator))
        .getOrElse { throw new Exception(s"no data at $extent") }
    }
  }
}
