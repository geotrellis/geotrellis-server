package geotrellis.server.ogc

import com.azavea.stac4s.StacCollection
import geotrellis.proj4.CRS
import geotrellis.raster.{RasterSource, ResampleMethod}
import geotrellis.raster.io.geotiff.OverviewStrategy
import geotrellis.server.ogc.style.OgcStyle
import geotrellis.server.ogc.utils._
import geotrellis.stac.raster.StacSource
import geotrellis.vector.{Extent, ProjectedExtent}

/**
 * An imagery source with a [[RasterSource]] that defines its capacities
 */
case class StacOgcSource(
  name: String,
  title: String,
  stacSource: StacSource[StacCollection],
  defaultStyle: Option[String],
  styles: List[OgcStyle],
  resampleMethod: ResampleMethod,
  overviewStrategy: OverviewStrategy,
  timeMetadataKey: Option[String]
) extends RasterOgcSource {
  lazy val time: OgcTime = attributes.time(timeMetadataKey).orElse(source.attributes.time(timeMetadataKey)).getOrElse(source.time(timeMetadataKey))

  def source: RasterSource = stacSource.source

  override def extentIn(crs: CRS): Extent = projectedExtent.reproject(crs).extent

  override def projectedExtent: ProjectedExtent = stacSource.projectedExtent
  override lazy val attributes: Map[String, String] = stacSource.attributes

  def toLayer(crs: CRS, style: Option[OgcStyle] = None): SimpleOgcLayer =
    SimpleOgcLayer(name, title, crs, source, style, resampleMethod, overviewStrategy)
}