package geotrellis.stac.raster

import geotrellis.proj4.{CRS, LatLng}
import geotrellis.raster.{RasterSource, SourceName}
import geotrellis.vector.{Extent, ProjectedExtent}

trait StacSource[T] {
  val crs: CRS = LatLng
  def projectedExtent: ProjectedExtent = ProjectedExtent(extent, crs)

  def asset: T
  def extent: Extent
  def name: SourceName
  // native projection can be not LatLng
  def source: RasterSource

  def attributes: Map[String, String]
}
