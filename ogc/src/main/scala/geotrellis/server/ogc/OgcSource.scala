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

package geotrellis.server.ogc

import geotrellis.proj4.CRS
import geotrellis.raster._
import geotrellis.raster.io.geotiff.OverviewStrategy
import geotrellis.server.extent.SampleUtils
import geotrellis.server.ogc.style._
import geotrellis.server.ogc.wms.CapabilitiesView
import geotrellis.store.{GeoTrellisPath, GeoTrellisRasterSource}
import geotrellis.vector.{Extent, ProjectedExtent}

import cats.data.{NonEmptyList => NEL}
import cats.syntax.option._
import com.azavea.maml.ast.Expression
import jp.ne.opt.chronoscala.Imports._
import opengis.wms.BoundingBox

import java.time.ZonedDateTime

/**
 * This trait and its implementing types should be jointly sufficient, along with a WMS 'GetMap'
 *  (or a WMTS 'GetTile' or a WCS 'GetCoverage' etc etc) request to produce a visual layer
 *  (represented more fully by [[OgcLayer]].
 *  This type represents *merely* that there is some backing by which valid OGC layers
 *  can be realized. Its purpose is to provide the appropriate level of abstraction for OGC
 *  services to conveniently reuse the same data about underlying imagery
 */
trait OgcSource {
  def name: String
  def title: String
  def defaultStyle: Option[String]
  def styles: List[OgcStyle]
  def nativeExtent: Extent
  def nativeRE: GridExtent[Long]
  def extentIn(crs: CRS): Extent
  def bboxIn(crs: CRS): BoundingBox
  def nativeCrs: Set[CRS]
  def metadata: RasterMetadata
  def attributes: Map[String, String]
  def resampleMethod: ResampleMethod
  def overviewStrategy: OverviewStrategy
  def time: OgcTime

  def nativeProjectedExtent: ProjectedExtent = ProjectedExtent(nativeExtent, nativeCrs.head)
}

trait RasterOgcSource extends OgcSource {
  def source: RasterSource

  def extentIn(crs: CRS): Extent = {
    val reprojected = source.reproject(crs)
    reprojected.extent
  }

  def bboxIn(crs: CRS): BoundingBox = {
    val reprojected = source.reproject(crs)
    CapabilitiesView.boundingBox(crs, reprojected.extent, reprojected.cellSize)
  }

  lazy val nativeRE: GridExtent[Long]      = source.gridExtent
  lazy val nativeCrs: Set[CRS]             = Set(source.crs)
  lazy val nativeExtent: Extent            = source.extent
  lazy val metadata: RasterMetadata        = source.metadata
  lazy val attributes: Map[String, String] = metadata.attributes
}

/**
 * An imagery source with a [[RasterSource]] that defines its capacities
 */
case class SimpleSource(
  name: String,
  title: String,
  source: RasterSource,
  defaultStyle: Option[String],
  styles: List[OgcStyle],
  resampleMethod: ResampleMethod,
  overviewStrategy: OverviewStrategy,
  timeMetadataKey: Option[String]
) extends RasterOgcSource {
  lazy val time: OgcTime =
    timeMetadataKey.flatMap { key =>
      source match {
        case mrs: MosaicRasterSource =>
          val times = mrs.metadata.list.toList.flatMap(_.attributes.get(key)).map(ZonedDateTime.parse)
          times match {
            case head :: tail => OgcTimePositions(NEL(head, tail)).some
            case _ => None
          }

        case _ =>
          source
            .metadata
            .attributes
            .get(key)
            .map(ZonedDateTime.parse)
            .map(OgcTimePositions(_))
      }
    }.getOrElse(OgcTimeEmpty)

  def isTemporal: Boolean = timeMetadataKey.nonEmpty && time.nonEmpty
}

case class GeoTrellisOgcSource(
  name: String,
  title: String,
  sourceUri: String,
  defaultStyle: Option[String],
  styles: List[OgcStyle],
  resampleMethod: ResampleMethod,
  overviewStrategy: OverviewStrategy,
  timeMetadataKey: String = "times"
) extends RasterOgcSource {

  private val dataPath = GeoTrellisPath.parse(sourceUri)

  lazy val source = GeoTrellisRasterSource(dataPath)

  lazy val time: OgcTime =
    if (!source.isTemporal) OgcTimeEmpty
    else OgcTimePositions(source.times)

  /**
   * If temporal, try to match in the following order:
   *
   * OgcTimeInterval behavior
   *  1. To the closest time in known valid source times
   *  2. To time.start
   *  3. To the passed interval.start
   *
   *  OgcTimePosition:
   * 1. finds the exact match
   *
   *  @note If case 3 is matched, read queries to the returned
   *        RasterSource may return zero results.
   *
   * @param interval
   * @return
   */
  def sourceForTime(interval: OgcTime): GeoTrellisRasterSource =
    if (source.isTemporal) {
      time match {
        case sourceInterval: OgcTimeInterval =>
          source.times.find { t =>
            interval match {
              case OgcTimeInterval(start, end, _) => start <= t && t <= end
              case OgcTimePositions(list)         => list.exists(_ == t)
              case OgcTimeEmpty                   => false
            }
          }.fold(sourceForTime(sourceInterval))(sourceForTime)

        case OgcTimePositions(NEL(head, _)) =>
          source.times.find { t =>
            interval match {
              case OgcTimeInterval(start, end, _) => start <= t && t <= end
              case OgcTimePositions(list)         => list.exists(_ == t)
              case OgcTimeEmpty                   => false
            }
          }.fold(sourceForTime(head))(sourceForTime)
        case _ => source
      }
    } else source

  def sourceForTime(time: ZonedDateTime): GeoTrellisRasterSource =
    if (source.isTemporal) GeoTrellisRasterSource(dataPath, Some(time))
    else source
}

case class MapAlgebraSourceMetadata(
  name: SourceName,
  crs: CRS,
  bandCount: Int,
  cellType: CellType,
  gridExtent: GridExtent[Long],
  resolutions: List[CellSize],
  sources: Map[String, RasterMetadata]
) extends RasterMetadata {
  /** MapAlgebra metadata usually doesn't contain a metadata that is common for all RasterSources */
  def attributes: Map[String, String] = Map.empty
  def attributesForBand(band: Int): Map[String, String] = Map.empty
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
  defaultStyle: Option[String],
  styles: List[OgcStyle],
  resampleMethod: ResampleMethod,
  overviewStrategy: OverviewStrategy
) extends OgcSource {
  def extentIn(crs: CRS): Extent = {
    val reprojectedSources: NEL[RasterSource] =
      NEL.fromListUnsafe(sources.values.map(_.reproject(crs)).toList)
    val extents =
      reprojectedSources.map(_.extent)

    SampleUtils.intersectExtents(extents).getOrElse {
      throw new Exception("no intersection found among map map algebra sources")
    }
  }

  def bboxIn(crs: CRS): BoundingBox = {
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

  lazy val metadata: MapAlgebraSourceMetadata =
    MapAlgebraSourceMetadata(
      StringName(name),
      nativeCrs.head,
      minBandCount,
      cellTypes.head,
      nativeRE,
      resolutions,
      sources.mapValues(_.metadata)
    )

  lazy val nativeExtent: Extent = {
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

  lazy val nativeRE: GridExtent[Long] = {
    val reprojectedSources: NEL[RasterSource] =
      NEL.fromListUnsafe(sources.values.map(_.reproject(nativeCrs.head)).toList)
    val cellSize =
      SampleUtils.chooseSmallestCellSize(reprojectedSources.map(_.cellSize))

    new GridExtent[Long](nativeExtent, cellSize)
  }

  val time: OgcTime                         = OgcTimeEmpty
  val attributes: Map[String, String]       = Map.empty
  lazy val nativeCrs: Set[CRS]              = sources.values.map(_.crs).toSet
  lazy val minBandCount: Int                = sources.values.map(_.bandCount).min
  lazy val cellTypes: Set[CellType]         = sources.values.map(_.cellType).toSet
  lazy val resolutions: List[CellSize]      = sources.values.flatMap(_.resolutions).toList.distinct
}
