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

package geotrellis.server.ogc.style

import geotrellis.proj4.CRS
import geotrellis.raster.{MultibandTile, Raster}
import geotrellis.raster.histogram.Histogram
import geotrellis.raster.io.geotiff.GeoTiff
import geotrellis.server.ogc.OutputFormat

case class RGBStyle(name: String, title: String, legends: List[LegendModel] = Nil) extends OgcStyle {
  def renderRaster(raster: Raster[MultibandTile], crs: CRS, format: OutputFormat, hists: List[Histogram[Double]]): Array[Byte] = {
    format match {
      case _: OutputFormat.Png  => raster.tile.renderPng()
      case OutputFormat.Jpg     => raster.tile.renderJpg()
      case OutputFormat.GeoTiff => GeoTiff(raster, crs).toCloudOptimizedByteArray
    }
  }
}
