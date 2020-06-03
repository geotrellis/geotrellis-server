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

import geotrellis.server._
import geotrellis.server.ogc.style._
import geotrellis.raster._
import geotrellis.raster.resample._
import geotrellis.raster.io.geotiff._
import geotrellis.raster.reproject.ReprojectRasterExtent
import geotrellis.raster.reproject.Reproject.Options
import geotrellis.vector.Extent
import geotrellis.proj4.CRS
import com.azavea.maml.ast._
import cats.effect._
import cats.data.{NonEmptyList => NEL}

/**
 * OgcLayer instances are sufficient to produce visual rasters as the end product of 'get map'
 *  requests. They are produced from a combination of a WMS 'GetMap'
 *  (or whatever the analogous request in whatever OGC service is being produced) and an instance
 *  of [[OgcSource]]
 */
sealed trait OgcLayer {
  def name: String
  def title: String
  def crs: CRS
  def style: Option[OgcStyle]
  def resampleMethod: ResampleMethod
  def overviewStrategy: OverviewStrategy
}

sealed trait RasterOgcLayer {
  def source: RasterSource
}

case class SimpleOgcLayer(
  name: String,
  title: String,
  crs: CRS,
  source: RasterSource,
  style: Option[OgcStyle],
  resampleMethod: ResampleMethod,
  overviewStrategy: OverviewStrategy
) extends OgcLayer with RasterOgcLayer

case class MapAlgebraOgcLayer(
  name: String,
  title: String,
  crs: CRS,
  parameters: Map[String, SimpleOgcLayer],
  algebra: Expression,
  style: Option[OgcStyle],
  resampleMethod: ResampleMethod,
  overviewStrategy: OverviewStrategy
) extends OgcLayer

object SimpleOgcLayer {
  val logger = org.log4s.getLogger
  implicit val simpleOgcReification = new ExtentReification[SimpleOgcLayer] {
    def extentReification(self: SimpleOgcLayer)(implicit contextShift: ContextShift[IO]): (Extent, CellSize) => IO[ProjectedRaster[MultibandTile]] =
      (extent: Extent, cs: CellSize) =>  IO {
        val targetGrid = new GridExtent[Long](extent, cs)
        println(s"attempting to retrieve layer $self at extent $extent with $cs ${targetGrid.cols}x${targetGrid.rows}")
        println(s"Requested extent geojson: ${extent.toGeoJson}")
        val raster: Raster[MultibandTile] = self.source
          .reprojectToRegion(self.crs, targetGrid.toRasterExtent, self.resampleMethod, self.overviewStrategy)
          .read(extent)
          .getOrElse(throw new Exception(s"Unable to retrieve layer $self at extent $extent $cs"))
        println(s"Successfully retrieved layer $self at extent $extent with f $cs ${targetGrid.cols}x${targetGrid.rows}")

        ProjectedRaster(raster, self.crs)
      }
  }

  implicit val simpleOgcHasRasterExtents: HasRasterExtents[SimpleOgcLayer] = new HasRasterExtents[SimpleOgcLayer] {
    def rasterExtents(self: SimpleOgcLayer)(implicit contextShift: ContextShift[IO]): IO[NEL[RasterExtent]] =
      IO {
        val rasterExtents = self.source.resolutions.map { cs =>
          val re = RasterExtent(self.source.extent, cs)
          ReprojectRasterExtent(re, self.source.crs, self.crs, Options.DEFAULT.copy(method = self.resampleMethod))
        }
        NEL.fromList(rasterExtents).get
      }
  }
}
