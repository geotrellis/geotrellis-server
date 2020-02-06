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

import geotrellis.proj4._
import geotrellis.raster.io.geotiff.{Auto, AutoHigherResolution, Base, OverviewStrategy}
import geotrellis.raster.resample.{NearestNeighbor, ResampleMethod}
import geotrellis.raster._
import geotrellis.layer._
import geotrellis.vector._

/**
  * TODO: remove after upgrading up to GeoTrellis 3.3.0
  * Note: GeoTrellis AttributeStore does not store the band count for the layers by default,
  *       thus they need to be provided from application configuration.
  *
  * @param dataPath geotrellis catalog DataPath
  */
class GeoTrellisRasterSourceLegacy(
  attributeStore: AttributeStore,
  dataPath: GeoTrellisPath,
  sourceLayers: Stream[Layer],
  targetCellType: Option[TargetCellType]
) extends GeoTrellisRasterSource(attributeStore, dataPath, sourceLayers, targetCellType) {
  def this(attributeStore: AttributeStore, dataPath: GeoTrellisPath) =
    this(
      attributeStore,
      dataPath,
      GeoTrellisRasterSource.getSourceLayersByName(attributeStore, dataPath.layerName, dataPath.bandCount.getOrElse(1)),
      None
    )

  def this(dataPath: GeoTrellisPath) = this(AttributeStore(dataPath.value), dataPath)

  override def read(extent: Extent, bands: Seq[Int]): Option[Raster[MultibandTile]] = {
    GeoTrellisRasterSourceLegacy.read(reader, layerId, layerMetadata, extent, bands).map(convertRaster)
  }

  override def reprojection(targetCRS: CRS, resampleTarget: ResampleTarget = DefaultTarget, method: ResampleMethod = NearestNeighbor, strategy: OverviewStrategy = AutoHigherResolution): RasterSource = {
    if (targetCRS != this.crs) {
      val reprojectOptions = ResampleTarget.toReprojectOptions(this.gridExtent, resampleTarget, method)
      val (closestLayerId, targetGridExtent) = GeoTrellisReprojectRasterSource.getClosestSourceLayer(targetCRS, sourceLayers, reprojectOptions, strategy)
      new GeoTrellisReprojectRasterSourceLegacy(attributeStore, dataPath, closestLayerId, sourceLayers, targetGridExtent, targetCRS, resampleTarget, targetCellType = targetCellType)
    } else {
      // TODO: add unit tests for this in particular, the behavior feels murky
      resampleTarget match {
        case DefaultTarget =>
          // I think I was asked to do nothing
          this
        case resampleTarget =>
          val resampledGridExtent = resampleTarget(this.gridExtent)
          val closestLayerId = GeoTrellisRasterSource.getClosestResolution(sourceLayers.toList, resampledGridExtent.cellSize, strategy)(_.metadata.layout.cellSize).get.id
          new GeoTrellisResampleRasterSourceLegacy(attributeStore, dataPath, closestLayerId, sourceLayers, resampledGridExtent, method, targetCellType)
      }
    }
  }

  override def resample(resampleTarget: ResampleTarget, method: ResampleMethod, strategy: OverviewStrategy): RasterSource = {
    val resampledGridExtent = resampleTarget(this.gridExtent)
    val closestLayerId = GeoTrellisRasterSource.getClosestResolution(sourceLayers.toList, resampledGridExtent.cellSize, strategy)(_.metadata.layout.cellSize).get.id
    new GeoTrellisResampleRasterSourceLegacy(attributeStore, dataPath, closestLayerId, sourceLayers, resampledGridExtent, method, targetCellType)
  }

  override def convert(targetCellType: TargetCellType): RasterSource =
    new GeoTrellisRasterSourceLegacy(attributeStore, dataPath, sourceLayers, Some(targetCellType))

  override def toString: String =
    s"GeoTrellisRasterSourceLegacy($dataPath, $layerId)"
}


object GeoTrellisRasterSourceLegacy {
  import GeoTrellisRasterSource._

  // stable identifiers to match in a readTiles function
  private val SpatialKeyClass    = classOf[SpatialKey]
  private val TileClass          = classOf[Tile]
  private val MultibandTileClass = classOf[MultibandTile]

  /** Read metadata for all layers that share a name and sort them by their resolution */
  def getSourceLayersByName(attributeStore: AttributeStore, layerName: String, bandCount: Int): Stream[Layer] = {
    attributeStore.
      layerIds.
      filter(_.name == layerName).
      sortWith(_.zoom > _.zoom).
      toStream. // We will be lazy about fetching higher zoom levels
      map { id =>
        val metadata = attributeStore.readMetadata[TileLayerMetadata[SpatialKey]](id)
        Layer(id, metadata, bandCount)
      }
  }

  def readTiles(reader: CollectionLayerReader[LayerId], layerId: LayerId, extent: Extent, bands: Seq[Int]): Seq[(SpatialKey, MultibandTile)] with Metadata[TileLayerMetadata[SpatialKey]] = {
    def spatialTileRead =
      reader.query[SpatialKey, Tile, TileLayerMetadata[SpatialKey]](layerId)
        .where(Intersects(extent))
        .result
        .withContext(tiles =>
          // Convert single band tiles to multiband
          tiles.map { case (key, tile) => (key, MultibandTile(tile)) }
        )

    def spatialMultibandTileRead =
      reader.query[SpatialKey, MultibandTile, TileLayerMetadata[SpatialKey]](layerId)
        .where(Intersects(extent))
        .result
        .withContext(tiles =>
          tiles.map { case (key, tile) => (key, tile.subsetBands(bands)) }
        )

    val header = reader.attributeStore.readHeader[LayerHeader](layerId)

    if (!header.keyClass.contains("spark")) {
      (Class.forName(header.keyClass), Class.forName(header.valueClass)) match {
        case (SpatialKeyClass, TileClass) => spatialTileRead
        case (SpatialKeyClass, MultibandTileClass) => spatialMultibandTileRead
        case _ =>
          throw new Exception(s"Unable to read single or multiband tiles from file: ${(header.keyClass, header.valueClass)}")
      }
    } else {
      /**
        * Legacy GeoTrellis Layers compact
        * TODO: remove in GT 4.0
        */
      (header.keyClass, header.valueClass) match {
        case ("geotrellis.spark.SpatialKey", "geotrellis.raster.Tile") => spatialTileRead
        case ("geotrellis.spark.SpatialKey", "geotrellis.raster.MultibandTile") => spatialMultibandTileRead
        case _ =>
          throw new Exception(s"Unable to read single or multiband tiles from file: ${(header.keyClass, header.valueClass)}")
      }
    }
  }

  def readIntersecting(reader: CollectionLayerReader[LayerId], layerId: LayerId, metadata: TileLayerMetadata[SpatialKey], extent: Extent, bands: Seq[Int]): Option[Raster[MultibandTile]] = {
    val tiles = readTiles(reader, layerId, extent, bands)
    sparseStitch(tiles, extent)
  }

  def read(reader: CollectionLayerReader[LayerId], layerId: LayerId, metadata: TileLayerMetadata[SpatialKey], extent: Extent, bands: Seq[Int]): Option[Raster[MultibandTile]] = {
    val tiles = readTiles(reader, layerId, extent, bands)
    metadata.extent.intersection(extent) flatMap { intersectionExtent =>
      sparseStitch(tiles, intersectionExtent).map(_.crop(intersectionExtent))
    }
  }
}
