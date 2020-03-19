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
import geotrellis.proj4.WebMercator
import geotrellis.raster._
import geotrellis.raster.geotiff.GeoTiffRasterSource
import geotrellis.raster.io.geotiff.AutoHigherResolution

import geotrellis.vector.Extent

import _root_.io.circe._
import _root_.io.circe.generic.semiauto._
import cats.effect._
import cats.data.{NonEmptyList => NEL}

import java.net.URI

case class GeoTiffNode(uri: URI, band: Int, celltype: Option[CellType], resampleMethod: ResampleMethod)

object GeoTiffNode extends RasterSourceUtils {
  def getRasterSource(uri: String): GeoTiffRasterSource = GeoTiffRasterSource(uri)

  implicit val cogNodeEncoder: Encoder[GeoTiffNode] = deriveEncoder[GeoTiffNode]
  implicit val cogNodeDecoder: Decoder[GeoTiffNode] = deriveDecoder[GeoTiffNode]

  implicit val cogNodeRasterExtents: HasRasterExtents[GeoTiffNode] = new HasRasterExtents[GeoTiffNode] {
    def rasterExtents(self: GeoTiffNode)(implicit contextShift: ContextShift[IO]): IO[NEL[RasterExtent]] =
      getRasterExtents(self.uri.toString)
  }

  implicit val cogNodeTmsReification: TmsReification[GeoTiffNode] = new TmsReification[GeoTiffNode] {
    def tmsReification(self: GeoTiffNode, buffer: Int)(implicit contextShift: ContextShift[IO]): (Int, Int, Int) => IO[ProjectedRaster[MultibandTile]] = (z: Int, x: Int, y: Int) => {
      def fetch(xCoord: Int, yCoord: Int) =
        fetchTile(self.uri.toString, z, xCoord, yCoord, WebMercator)
          .map(_.tile)
          .map(_.band(self.band))

      fetch(x, y).map { tile =>
        val extent = tmsLevels(z).mapTransform.keyToExtent(x, y)
        ProjectedRaster(MultibandTile(tile), extent, WebMercator)
      }
    }
  }

  implicit val CogNodeExtentReification: ExtentReification[GeoTiffNode] = new ExtentReification[GeoTiffNode] {
    def extentReification(self: GeoTiffNode)(implicit contextShift: ContextShift[IO]): (Extent, CellSize) => IO[ProjectedRaster[MultibandTile]] = (extent: Extent, cs: CellSize) => {
      getRasterSource(self.uri.toString)
        .resample(TargetRegion(new GridExtent[Long](extent, cs)), self.resampleMethod, AutoHigherResolution)
        .read(extent, self.band :: Nil)
        .map { ProjectedRaster(_, WebMercator) }
        .toIO { new Exception(s"No tile avail for RasterExtent: ${RasterExtent(extent, cs)}") }
    }
  }
}
