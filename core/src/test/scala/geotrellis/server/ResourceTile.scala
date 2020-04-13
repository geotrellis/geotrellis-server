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

package geotrellis.server

import geotrellis.server.vlm._
import geotrellis.raster._
import geotrellis.raster.geotiff._
import geotrellis.raster.io.geotiff.OverviewStrategy
import geotrellis.raster.resample._
import geotrellis.vector.Extent

import cats.effect._
import cats.data.{NonEmptyList => NEL}

case class ResourceTile(
  name: String,
  resampleMethod: ResampleMethod = ResampleMethod.DEFAULT,
  overviewStrategy: OverviewStrategy = OverviewStrategy.DEFAULT
) {
  def uri: String = s"file://${getClass.getResource(s"/$name").getFile}"
}

object ResourceTile extends RasterSourceUtils {
  def getRasterSource(uri: String): RasterSource = GeoTiffRasterSource(uri)

  implicit val extentReification: ExtentReification[ResourceTile] = new ExtentReification[ResourceTile] {
    def extentReification(self: ResourceTile)(implicit contextShift: ContextShift[IO]): (Extent, CellSize) => IO[ProjectedRaster[MultibandTile]] =
      (extent: Extent, cs: CellSize) => {
        val rs = getRasterSource(self.uri.toString)
        rs.resample(TargetRegion(new GridExtent[Long](extent, cs)), self.resampleMethod, self.overviewStrategy)
          .read(extent)
          .map { raster => ProjectedRaster(raster, rs.crs) }
          .toIO { new Exception(s"No tile avail for RasterExtent: ${RasterExtent(extent, cs)}") }
      }
  }

  implicit val nodeRasterExtents: HasRasterExtents[ResourceTile] = new HasRasterExtents[ResourceTile] {
    def rasterExtents(self: ResourceTile)(implicit contextShift: ContextShift[IO]): IO[NEL[RasterExtent]] =
      getRasterExtents(self.uri.toString)
  }

}
