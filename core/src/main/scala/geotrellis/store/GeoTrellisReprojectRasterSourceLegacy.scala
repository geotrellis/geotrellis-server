/*
 * Copyright 2019 Azavea
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

package geotrellis.store

import geotrellis.vector._
import geotrellis.raster._
import geotrellis.raster.reproject._
import geotrellis.raster.resample._
import geotrellis.proj4._
import geotrellis.raster.io.geotiff.{AutoHigherResolution, OverviewStrategy}

import org.log4s._
import scala.io.AnsiColor._

/** TODO: remove after upgrading up to GeoTrellis 3.3.0 */
class GeoTrellisReprojectRasterSourceLegacy(
  attributeStore: AttributeStore,
  dataPath: GeoTrellisPath,
  layerId: LayerId,
  sourceLayers: Stream[Layer],
  gridExtent: GridExtent[Long],
  crs: CRS,
  resampleTarget: ResampleTarget = DefaultTarget,
  resampleMethod: ResampleMethod = NearestNeighbor,
  strategy: OverviewStrategy = AutoHigherResolution,
  errorThreshold: Double = 0.125,
  targetCellType: Option[TargetCellType]
) extends GeoTrellisReprojectRasterSource(attributeStore, dataPath, layerId, sourceLayers, gridExtent, crs, resampleTarget, resampleMethod, strategy, errorThreshold, targetCellType) {
  @transient private[this] lazy val logger = getLogger

  override def read(extent: Extent, bands: Seq[Int]): Option[Raster[MultibandTile]] = {
    val transform = Transform(sourceLayer.metadata.crs, crs)
    val backTransform = Transform(crs, sourceLayer.metadata.crs)
    for {
      subExtent <- this.extent.intersection(extent)
      targetRasterExtent = this.gridExtent.createAlignedRasterExtent(subExtent)
      sourceExtent = targetRasterExtent.extent.reprojectAsPolygon(backTransform, 0.001).getEnvelopeInternal
      sourceRegion = sourceLayer.metadata.layout.createAlignedGridExtent(sourceExtent)
      _ = {
        lazy val tileBounds = sourceLayer.metadata.mapTransform.extentToBounds(sourceExtent)
        lazy val pixelsRead = (tileBounds.size * sourceLayer.metadata.layout.tileCols * sourceLayer.metadata.layout.tileRows).toDouble
        lazy val pixelsQueried = targetRasterExtent.cols.toDouble * targetRasterExtent.rows.toDouble
        def msg = s"""
          |${GREEN}Read($extent)${RESET} =
          |\t${BOLD}FROM${RESET} ${dataPath.toString} ${sourceLayer.id}
          |\t${BOLD}SOURCE${RESET} $sourceExtent ${sourceLayer.metadata.cellSize} @ ${sourceLayer.metadata.crs}
          |\t${BOLD}TARGET${RESET} ${targetRasterExtent.extent} ${targetRasterExtent.cellSize} @ ${crs}
          |\t${BOLD}READ${RESET} ${pixelsRead/pixelsQueried} read/query ratio for ${tileBounds.size} tiles
        """.stripMargin
        if (tileBounds.size < 1024) // Assuming 256x256 tiles this would be a very large request
          logger.debug(msg)
        else
          logger.warn(msg + " (large read)")
      }
      raster <- GeoTrellisRasterSourceLegacy.readIntersecting(reader, layerId, sourceLayer.metadata, sourceExtent, bands)
    } yield {
      val reprojected = raster.reproject(
        targetRasterExtent,
        transform,
        backTransform,
        ResampleTarget.toReprojectOptions(targetRasterExtent.toGridType[Long], resampleTarget, resampleMethod)
      )
      convertRaster(reprojected)
    }
  }

  override def reprojection(targetCRS: CRS, resampleTarget: ResampleTarget = DefaultTarget, method: ResampleMethod = NearestNeighbor, strategy: OverviewStrategy = AutoHigherResolution): RasterSource = {
    if (targetCRS == sourceLayer.metadata.crs) {
      val resampledGridExtent = resampleTarget(this.sourceLayer.gridExtent)
      val closestLayer = GeoTrellisRasterSource.getClosestResolution(sourceLayers, resampledGridExtent.cellSize, strategy)(_.metadata.layout.cellSize).get
      // TODO: if closestLayer is w/in some marging of desired CellSize, return GeoTrellisRasterSource instead
      new GeoTrellisResampleRasterSource(attributeStore, dataPath, closestLayer.id, sourceLayers, resampledGridExtent, resampleMethod, targetCellType)
    } else {
      // Pick new layer ID
      val (closestLayerId, gridExtent) =
        GeoTrellisReprojectRasterSource
          .getClosestSourceLayer(
            targetCRS,
            sourceLayers,
            ResampleTarget.toReprojectOptions(this.gridExtent, resampleTarget, resampleMethod),
            strategy
          )
      new GeoTrellisReprojectRasterSourceLegacy(
        attributeStore,
        dataPath,
        layerId,
        sourceLayers,
        gridExtent,
        targetCRS,
        resampleTarget,
        resampleMethod,
        targetCellType = targetCellType
      )
    }
  }

  override def resample(resampleTarget: ResampleTarget, method: ResampleMethod, strategy: OverviewStrategy): RasterSource = {
    val newReprojectOptions = ResampleTarget.toReprojectOptions(this.gridExtent, resampleTarget, method)
    val (closestLayerId, newGridExtent) = GeoTrellisReprojectRasterSource.getClosestSourceLayer(crs, sourceLayers, newReprojectOptions, strategy)
    new GeoTrellisReprojectRasterSourceLegacy(attributeStore, dataPath, closestLayerId, sourceLayers, newGridExtent, crs, resampleTarget, targetCellType = targetCellType)
  }

  override def convert(targetCellType: TargetCellType): RasterSource = {
    new GeoTrellisReprojectRasterSourceLegacy(attributeStore, dataPath, layerId, sourceLayers, gridExtent, crs, resampleTarget, targetCellType = Some(targetCellType))
  }

  override def toString: String =
    s"GeoTrellisReprojectRasterSourceLegacy(${dataPath.value},$layerId,$crs,$gridExtent,${resampleMethod})"
}
