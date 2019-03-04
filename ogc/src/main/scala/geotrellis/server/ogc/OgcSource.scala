package geotrellis.server.ogc

import geotrellis.server._
import geotrellis.server.extent.SampleUtils
import geotrellis.server.ogc.wms._
import geotrellis.server.ExtentReification.ops._

import geotrellis.contrib.vlm._
import geotrellis.raster.CellSize
import geotrellis.vector.Extent
import geotrellis.proj4.{CRS, WebMercator}
import com.azavea.maml.ast._
import cats.effect._
import cats.implicits._
import cats.data.{NonEmptyList => NEL}
import opengis.wms.BoundingBox

/** This trait and its implementing types are jointly sufficienty, along with a WMS 'get map'
 *  request to produce a visual layer (represented more fully by the [[OgcLayer]] hierarchy.
 *  This type represents *merely* that there is some backing by which valid OGC layers
 *  can be realized.
 */
trait OgcSource {
  def name: String
  def styles: List[StyleModel]
  def bboxIn(crs: CRS): BoundingBox
  def nativeCrs: Set[CRS]
}

case class SimpleSource(
  name: String,
  title: String,
  source: RasterSource,
  styles: List[StyleModel]
) extends OgcSource {

  def bboxIn(crs: CRS) = {
    val reprojected = source.reproject(crs)
    CapabilitiesView.boundingBox(crs, reprojected.extent, reprojected.cellSize)
  }

  def nativeCrs: Set[CRS] = Set(source.crs)
}

case class MapAlgebraSource(
  name: String,
  title: String,
  sources: Map[String, RasterSource],
  algebra: Expression,
  styles: List[StyleModel]
) extends OgcSource {

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

  def nativeCrs: Set[CRS] = sources.values.map(_.crs).toSet

}
