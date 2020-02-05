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
import geotrellis.proj4._
import geotrellis.raster._
import geotrellis.raster.resample.{NearestNeighbor, ResampleMethod}
import geotrellis.raster.io.geotiff.{AutoHigherResolution, OverviewStrategy}

import org.log4s._

/**
 * TODO: remove after upgrading up to GeoTrellis 3.3.0
 *
 * RasterSource that resamples on read from underlying GeoTrellis layer.
 *
 * Note:
 * The constructor is unfriendly.
 * This class is not intended to constructed directly by the users.
 * Refer to [[GeoTrellisRasterSource]] for example of correct setup.
 * It is expected that the caller has significant pre-computed information  about the layers.
 *
 * @param attributeStore the source of metadata for the layers, used for reading
 * @param dataPath dataPath of the GeoTrellis catalog that can format a given path to be read in by a AttributeStore
 * @param layerId The specific layer we're sampling from
 * @param sourceLayers list of layers we can can sample from for futher resample
 * @param gridExtent the desired pixel grid for the layer
 * @param resampleMethod Resampling method used when fitting data to target grid
 */
class GeoTrellisResampleRasterSourceLegacy(
  attributeStore: AttributeStore,
  dataPath: GeoTrellisPath,
  layerId: LayerId,
  sourceLayers: Stream[Layer],
  gridExtent: GridExtent[Long],
  resampleMethod: ResampleMethod = NearestNeighbor,
  targetCellType: Option[TargetCellType] = None
) extends GeoTrellisResampleRasterSource(attributeStore, dataPath, layerId, sourceLayers, gridExtent, resampleMethod, targetCellType) {
  @transient private[this] lazy val logger = getLogger

  override def read(extent: Extent, bands: Seq[Int]): Option[Raster[MultibandTile]] = {
    val tileBounds = sourceLayer.metadata.mapTransform.extentToBounds(extent)
    def msg = s"\u001b[32mread($extent)\u001b[0m = ${dataPath.toString} ${sourceLayer.id} ${sourceLayer.metadata.cellSize} @ ${sourceLayer.metadata.crs} TO $cellSize -- reading ${tileBounds.size} tiles"
    if (tileBounds.size < 1024) // Assuming 256x256 tiles this would be a very large request
      logger.debug(msg)
    else
      logger.warn(msg + " (large read)")

    GeoTrellisRasterSourceLegacy.readIntersecting(reader, layerId, sourceLayer.metadata, extent, bands)
      .map { raster =>
        val targetRasterExtent = gridExtent.createAlignedRasterExtent(extent)
        logger.trace(s"\u001b[31mTargetRasterExtent\u001b[0m: ${targetRasterExtent} ${targetRasterExtent.dimensions}")
        raster.resample(targetRasterExtent, resampleMethod)
      }
  }

  override def reprojection(targetCRS: CRS, resampleTarget: ResampleTarget = DefaultTarget, method: ResampleMethod = NearestNeighbor, strategy: OverviewStrategy = AutoHigherResolution): GeoTrellisReprojectRasterSource = {
    val reprojectOptions = ResampleTarget.toReprojectOptions(this.gridExtent, resampleTarget, method)
    val (closestLayerId, gridExtent) = GeoTrellisReprojectRasterSource.getClosestSourceLayer(targetCRS, sourceLayers, reprojectOptions, strategy)
    new GeoTrellisReprojectRasterSourceLegacy(attributeStore, dataPath, layerId, sourceLayers, gridExtent, targetCRS, resampleTarget, targetCellType = targetCellType)
  }
  /** Resample underlying RasterSource to new grid extent
   * Note: ResampleTarget will be applied to GridExtent of the source layer, not the GridExtent of this RasterSource
   */
  override def resample(resampleTarget: ResampleTarget, method: ResampleMethod, strategy: OverviewStrategy): GeoTrellisResampleRasterSource = {
    val resampledGridExtent = resampleTarget(this.gridExtent)
    val closestLayer = GeoTrellisRasterSource.getClosestResolution(sourceLayers, resampledGridExtent.cellSize, strategy)(_.metadata.layout.cellSize).get
    // TODO: if closestLayer is w/in some marging of desired CellSize, return GeoTrellisRasterSource instead
    new GeoTrellisResampleRasterSourceLegacy(attributeStore, dataPath, closestLayer.id, sourceLayers, resampledGridExtent, method, targetCellType)
  }

  override def convert(targetCellType: TargetCellType): GeoTrellisResampleRasterSource = {
    new GeoTrellisResampleRasterSourceLegacy(attributeStore, dataPath, layerId, sourceLayers, gridExtent, resampleMethod, Some(targetCellType))
  }

  override def toString: String =
    s"GeoTrellisResampleRasterSourceLegacy(${dataPath.toString},$layerId,$gridExtent,$resampleMethod)"
}
