package geotrellis.stac.ogc

import geotrellis.proj4.CRS
import geotrellis.raster.io.geotiff.OverviewStrategy
import geotrellis.raster._
import geotrellis.server.ogc.style.OgcStyle
import geotrellis.server.ogc._
import geotrellis.vector.Extent
import opengis.wms.BoundingBox

case class StacOgcSource(
  name: String,
  title: String,
  source: RasterSource,
  defaultStyle: Option[String],
  styles: List[OgcStyle],
  resampleMethod: ResampleMethod,
  overviewStrategy: OverviewStrategy
) extends OgcSource {
  val timeInterval: Option[OgcTimeInterval] = None

  def nativeExtent: Extent = source.extent

  def nativeRE: GridExtent[Long] = source.gridExtent

  def extentIn(crs: CRS): Extent = ???

  def bboxIn(crs: CRS): BoundingBox = ???

  def nativeCrs: Set[CRS] = ???

  def metadata: RasterMetadata = ???

  def attributes: Map[String, String] = ???
}