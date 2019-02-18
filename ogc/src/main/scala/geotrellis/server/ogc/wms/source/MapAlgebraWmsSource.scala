package geotrellis.server.ogc.wms.source

import geotrellis.server.ogc.wms._
import geotrellis.server._
import geotrellis.server.extent.SampleUtils

import geotrellis.server.ogc.wms
import geotrellis.contrib.vlm._
import geotrellis.raster.CellSize
import geotrellis.vector.Extent
import geotrellis.proj4.{CRS, WebMercator}
import com.azavea.maml.ast._
import cats.effect._
import cats.implicits._
import cats.data.{NonEmptyList => NEL}

case class MapAlgebraWmsSource(
  name: String,
  title: String,
  sources: Map[String, RasterSource],
  algebra: Expression,
  styles: List[StyleModel]
) extends WmsSource {

  def bboxIn(crs: CRS) = {
    val reprojectedSources: NEL[RasterSource] =
      NEL.fromListUnsafe(sources.values.map(_.reproject(crs)).toList)
    val extents =
      reprojectedSources.map(_.extent)
    val extentIntersection =
      SampleUtils.intersectExtents(extents)
    val cellSize =
      SampleUtils.chooseLargestCellSize(reprojectedSources.map(_.cellSize))

    extentIntersection match {
      case Some(extent) =>
        CapabilitiesView.boundingBox(crs, extent, cellSize)
      case None =>
        throw new Exception("no intersection found among map map algebra sources")
    }
  }

  def nativeCrs: CRS = WebMercator

}
