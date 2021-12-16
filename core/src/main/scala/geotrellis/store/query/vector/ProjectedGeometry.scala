package geotrellis.store.query.vector

import geotrellis.proj4.CRS
import geotrellis.vector.{io => _, _}
import io.circe.generic.JsonCodec

@JsonCodec
case class ProjectedGeometry(geom: Geometry, crs: CRS) {
  def reproject(dest: CRS): ProjectedGeometry = ProjectedGeometry(geom.reproject(crs, dest), dest)

  def intersects(that: ProjectedGeometry): Boolean = geom.intersects(that.reproject(crs).geom)
  def covers(that: ProjectedGeometry): Boolean     = geom.covers(that.reproject(crs).geom)
  def contains(that: ProjectedGeometry): Boolean   = geom.contains(that.reproject(crs).geom)
}

object ProjectedGeometry {
  def fromProjectedExtent(projectedExtent: ProjectedExtent): ProjectedGeometry =
    ProjectedGeometry(projectedExtent.extent.toPolygon(), projectedExtent.crs)

  def apply(extent: Extent, crs: CRS): ProjectedGeometry = fromProjectedExtent(ProjectedExtent(extent, crs))
}
