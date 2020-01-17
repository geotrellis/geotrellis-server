package geotrellis.server.ogc

import geotrellis.server.extent.SampleUtils
import geotrellis.server.ogc.wms._

import geotrellis.raster._
import geotrellis.vector.Extent
import geotrellis.proj4.CRS

import com.azavea.maml.ast._

import cats.data.{NonEmptyList => NEL}
import opengis.wms.BoundingBox

/**
 * This trait and its implementing types should be jointly sufficient, along with a WMS 'GetMap'
 *  (or a WMTS 'GetTile' or a WCS 'GetCoverage' etc etc) request to produce a visual layer
 *  (represented more fully by the [[OgcLayer]] hierarchy.
 *  This type represents *merely* that there is some backing by which valid OGC layers
 *  can be realized. Its purpose is to provide the appropriate level of abstraction for OGC
 *  services to conveniently reuse the same data about underlying imagery
 */
trait OgcSource {
  def name: String
  def title: String
  def styles: List[OgcStyle]
  def nativeExtent: Extent
  def nativeRE: GridExtent[Long]
  def extentIn(crs: CRS): Extent
  def bboxIn(crs: CRS): BoundingBox
  def nativeCrs: Set[CRS]
}

/**
 * An imagery source with a [[RasterSource]] that defines its capacities
 */
case class SimpleSource(
  name: String,
  title: String,
  source: RasterSource,
  styles: List[OgcStyle]
) extends OgcSource {

  lazy val nativeRE = source.gridExtent

  def extentIn(crs: CRS): Extent = {
    val reprojected = source.reproject(crs)
    reprojected.extent
  }

  def bboxIn(crs: CRS) = {
    val reprojected = source.reproject(crs)
    CapabilitiesView.boundingBox(crs, reprojected.extent, reprojected.cellSize)
  }

  lazy val nativeCrs: Set[CRS] = Set(source.crs)

  lazy val nativeExtent: Extent = source.extent
}

/**
 * A complex layer, constructed from an [[Expression]] and one or more [[RasterSource]]
 *  mappings which allow evaluation of said [[Expression]]
 */
case class MapAlgebraSource(
  name: String,
  title: String,
  sources: Map[String, RasterSource],
  algebra: Expression,
  styles: List[OgcStyle]
) extends OgcSource {

  lazy val nativeExtent = {
    val reprojectedSources: NEL[RasterSource] =
      NEL.fromListUnsafe(sources.values.map(_.reproject(nativeCrs.head)).toList)
    val extents =
      reprojectedSources.map(_.extent)
    val extentIntersection =
      SampleUtils.intersectExtents(extents)

    extentIntersection match {
      case Some(extent) =>
        extent
      case None =>
        throw new Exception("no intersection found among map algebra sources")
    }
  }

  lazy val nativeRE = {
    val reprojectedSources: NEL[RasterSource] =
      NEL.fromListUnsafe(sources.values.map(_.reproject(nativeCrs.head)).toList)
    val cellSize =
      SampleUtils.chooseSmallestCellSize(reprojectedSources.map(_.cellSize))

    new GridExtent[Long](nativeExtent, cellSize)
  }

  def extentIn(crs: CRS): Extent = {
    val reprojectedSources: NEL[RasterSource] =
      NEL.fromListUnsafe(sources.values.map(_.reproject(crs)).toList)
    val extents =
      reprojectedSources.map(_.extent)

    SampleUtils.intersectExtents(extents).getOrElse {
      throw new Exception("no intersection found among map map algebra sources")
    }
  }

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

  lazy val nativeCrs: Set[CRS] = sources.values.map(_.crs).toSet

}
