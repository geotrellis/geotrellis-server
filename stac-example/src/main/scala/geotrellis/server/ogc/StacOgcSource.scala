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

import geotrellis.server.ogc.stac._

import com.azavea.stac4s.StacCollection
import geotrellis.proj4.CRS
import geotrellis.raster.{RasterSource, ResampleMethod}
import geotrellis.raster.io.geotiff.OverviewStrategy
import geotrellis.server.ogc.style.OgcStyle
import geotrellis.server.ogc.utils._
import geotrellis.stac.raster.StacSource
import geotrellis.vector.{Extent, ProjectedExtent}

/** An imagery source with a [[RasterSource]] that defines its capacities
  */
case class StacOgcSource(
  name: String,
  title: String,
  stacSource: StacSource[StacCollection],
  defaultStyle: Option[String],
  styles: List[OgcStyle],
  resampleMethod: ResampleMethod,
  overviewStrategy: OverviewStrategy,
  timeMetadataKey: Option[String],
  computeTimePositions: Boolean
) extends RasterOgcSource {

  lazy val time: OgcTime = {
    val summaryTime = if (!computeTimePositions) stacSource.stacExtent.ogcTime else None
    summaryTime
      .orElse(
        attributes
          .time(timeMetadataKey)
          .orElse(source.attributes.time(timeMetadataKey))
      )
      .getOrElse(source.time(timeMetadataKey))
  }

  def source: RasterSource = stacSource.source

  override def extentIn(crs: CRS): Extent = projectedExtent.reproject(crs).extent

  override def projectedExtent: ProjectedExtent     = stacSource.projectedExtent
  override lazy val attributes: Map[String, String] = stacSource.attributes

  def toLayer(crs: CRS, style: Option[OgcStyle], temporalSequence: List[OgcTime]): SimpleOgcLayer =
    SimpleOgcLayer(name, title, crs, source, style, resampleMethod, overviewStrategy)
}
