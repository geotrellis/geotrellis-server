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

package geotrellis.server.ogc.conf

import geotrellis.raster._
import geotrellis.raster.geotiff.GeoTiffRasterSource
import geotrellis.store.{GeoTrellisPath, GeoTrellisRasterSourceLegacy}

/**
 * Encodes an expectation that implementing classes be able to realize
 *  a geotrellis-contrib [[RasterSource]].
 */
sealed trait RasterSourceConf {
  def toRasterSource: RasterSource
}

/** An avro-backed (geotrellis) raster source */
case class GeoTrellis(
  catalogUri: String,
  layer: String,
  zoom: Int,
  bandCount: Int
) extends RasterSourceConf {
  def toRasterSource = new GeoTrellisRasterSourceLegacy(GeoTrellisPath(catalogUri, layer, Some(zoom), Some(bandCount)))
}

/** A geotiff (COG) raster source */
case class GeoTiff(uri: String) extends RasterSourceConf {
  def toRasterSource = GeoTiffRasterSource(uri)
}
