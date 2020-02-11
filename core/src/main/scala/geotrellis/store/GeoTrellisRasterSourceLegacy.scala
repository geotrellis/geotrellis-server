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

import java.time.ZonedDateTime

import geotrellis.proj4._
import geotrellis.raster.io.geotiff.{AutoHigherResolution, OverviewStrategy}
import geotrellis.raster.resample.{NearestNeighbor, ResampleMethod}
import geotrellis.raster._
import geotrellis.layer._
import geotrellis.vector._
import _root_.io.circe.Decoder

case class LayerLegacy[K](id: LayerId, metadata: TileLayerMetadata[K], bandCount: Int) {
  /** GridExtent of the data pixels in the layer */
  def gridExtent: GridExtent[Long] = metadata.layout.createAlignedGridExtent(metadata.extent)
}

/**
 * TODO: remove after upgrading up to GeoTrellis 3.3.0
 * Note: GeoTrellis AttributeStore does not store the band count for the layers by default,
 *       thus they need to be provided from application configuration.
 *
 * @param attributeStore GeoTrellis attribute store
 * @param dataPath       GeoTrellis catalog DataPath
 * @param sourceLayers   List of source layers
 * @param targetCellType The target cellType

 * @tparam K Key type, SpatialKey or SpaceTimeKey depending on the
 */
class GeoTrellisRasterSourceLegacy[K: SpatialComponent: Decoder](
  val attributeStore: AttributeStore,
  val dataPath: GeoTrellisPath,
  val sourceLayers: Stream[LayerLegacy[K]],
  val targetCellType: Option[TargetCellType]
) extends RasterSource {
  def name: GeoTrellisPath = dataPath

  def this(attributeStore: AttributeStore, dataPath: GeoTrellisPath) =
    this(
      attributeStore,
      dataPath,
      GeoTrellisRasterSourceLegacy.getSourceLayersByName[K](attributeStore, dataPath.layerName, dataPath.bandCount.getOrElse(1)),
      None
    )

  def this(dataPath: GeoTrellisPath) = this(AttributeStore(dataPath.value), dataPath)

  def layerId: LayerId = dataPath.layerId

  lazy val reader = CollectionLayerReader(attributeStore, dataPath.value)

  // read metadata directly instead of searching sourceLayers to avoid unneeded reads
  lazy val layerMetadata = reader.attributeStore.readMetadata[TileLayerMetadata[K]](layerId)

  lazy val gridExtent: GridExtent[Long] = layerMetadata.layout.createAlignedGridExtent(layerMetadata.extent)

  val bandCount: Int = dataPath.bandCount.getOrElse(1)

  def crs: CRS = layerMetadata.crs

  def cellType: CellType = dstCellType.getOrElse(layerMetadata.cellType)

  def attributes: Map[String, String] = Map(
    "catalogURI" -> dataPath.value,
    "layerName"  -> layerId.name,
    "zoomLevel"  -> layerId.zoom.toString,
    "bandCount"  -> bandCount.toString
  )
  /** GeoTrellis metadata doesn't allow to query a per band metadata by default. */
  def attributesForBand(band: Int): Map[String, String] = Map.empty

  def metadata: GeoTrellisMetadata = GeoTrellisMetadata(name, crs, bandCount, cellType, gridExtent, resolutions, attributes)

  // reference to this will fully initilze the sourceLayers stream
  lazy val resolutions: List[CellSize] = sourceLayers.map(_.gridExtent.cellSize).toList

  override def read(extent: Extent, bands: Seq[Int]): Option[Raster[MultibandTile]] =
    GeoTrellisRasterSourceLegacy.read(reader, layerId, layerMetadata, extent, bands).map(convertRaster)

  def read(bounds: GridBounds[Long], bands: Seq[Int]): Option[Raster[MultibandTile]] =
    bounds
      .intersection(this.dimensions)
      .map(gridExtent.extentFor(_).buffer(- cellSize.width / 2, - cellSize.height / 2))
      .flatMap(read(_, bands))

  override def readExtents(extents: Traversable[Extent], bands: Seq[Int]): Iterator[Raster[MultibandTile]] =
    extents.toIterator.flatMap(read(_, bands))

  override def readBounds(bounds: Traversable[GridBounds[Long]], bands: Seq[Int]): Iterator[Raster[MultibandTile]] =
    bounds.toIterator.flatMap(_.intersection(this.dimensions).flatMap(read(_, bands)))

  override def reprojection(targetCRS: CRS, resampleTarget: ResampleTarget = DefaultTarget, method: ResampleMethod = NearestNeighbor, strategy: OverviewStrategy = AutoHigherResolution): RasterSource = {
    if (targetCRS != this.crs) {
      val reprojectOptions = ResampleTarget.toReprojectOptions(this.gridExtent, resampleTarget, method)
      val (closestLayerId, targetGridExtent) = GeoTrellisReprojectRasterSourceLegacy.getClosestSourceLayer(targetCRS, sourceLayers, reprojectOptions, strategy)
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
  def getSourceLayersByName[K: SpatialComponent: Decoder](attributeStore: AttributeStore, layerName: String, bandCount: Int): Stream[LayerLegacy[K]] = {
    attributeStore.
      layerIds.
      filter(_.name == layerName).
      sortWith(_.zoom > _.zoom).
      toStream. // We will be lazy about fetching higher zoom levels
      map { id =>
        val metadata = attributeStore.readMetadata[TileLayerMetadata[K]](id)
        LayerLegacy(id, metadata, bandCount)
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

    def spaceTimeTileRead =
      reader.query[SpaceTimeKey, Tile, TileLayerMetadata[SpaceTimeKey]](layerId)
        .where(Intersects(extent))
        .where(At(ZonedDateTime.now()))
        .result
        .withContext(tiles =>
          // Convert single band tiles to multiband
          tiles.map { case (key, tile) => (key, MultibandTile(tile)) }
        )

    def spaceTimeMultibandTileRead =
      reader.query[SpaceTimeKey, MultibandTile, TileLayerMetadata[SpaceTimeKey]](layerId)
        .where(Intersects(extent))
        .where(At(ZonedDateTime.now()))
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

  def readIntersecting(reader: CollectionLayerReader[LayerId], layerId: LayerId, metadata: TileLayerMetadata[_], extent: Extent, bands: Seq[Int]): Option[Raster[MultibandTile]] = {
    val tiles = readTiles(reader, layerId, extent, bands)
    sparseStitch(tiles, extent)
  }

  def read(reader: CollectionLayerReader[LayerId], layerId: LayerId, metadata: TileLayerMetadata[_], extent: Extent, bands: Seq[Int]): Option[Raster[MultibandTile]] = {
    val tiles = readTiles(reader, layerId, extent, bands)
    metadata.extent.intersection(extent) flatMap { intersectionExtent =>
      sparseStitch(tiles, intersectionExtent).map(_.crop(intersectionExtent))
    }
  }
}
