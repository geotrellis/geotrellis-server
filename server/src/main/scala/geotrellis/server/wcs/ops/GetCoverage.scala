package geotrellis.server.wcs.ops

import geotrellis.proj4._
import geotrellis.raster._
import geotrellis.raster.crop._
import geotrellis.raster.reproject._
import geotrellis.raster.io.geotiff._
import geotrellis.server.wcs.WcsService
import geotrellis.server.wcs.params.GetCoverageWcsParams
import geotrellis.spark._
import geotrellis.spark.io._
import geotrellis.spark.stitch._

import com.typesafe.config.ConfigFactory
import com.github.blemale.scaffeine.{Cache, Scaffeine}

import scala.util.Try
import scala.concurrent.duration._

class GetCoverage(catalogUri: => String) {
  /*
  QGIS appears to sample WCS service by placing low and high resolution requests at coverage center. 
  These sampling requests happen for every actual WCS request, we can get really great cache hit rates.
  */
  lazy val requestCache: Cache[(LayerId, GridBounds, RasterExtent), Raster[Tile]] =
      Scaffeine()
        .recordStats()
        .expireAfterWrite(1.hour)
        .maximumSize(32)
        .build()

  /*
  CollectionReader holds AttributeStore which has request cache.
  For weirdness with frequently changing layers inquire within.
  */
  lazy val collectionReader = CollectionLayerReader(catalogUri)

  /* This is a workaround for a caching but in AttributeStore*/
  lazy val altAttributeStore = AttributeStore(catalogUri)

  def build(catalog: WcsService.MetadataCatalog, params: GetCoverageWcsParams): Array[Byte] = {
    def as = collectionReader.attributeStore

    val (zooms, _) = catalog(params.identifier)
    val allMD = zooms.sorted.map{ z => (z, as.readMetadata[TileLayerMetadata[SpatialKey]](LayerId(params.identifier, z))) }
    val re = RasterExtent(params.boundingBox, params.width, params.height)
    val srcCrs = allMD.head._2.crs
    val srcRE = ReprojectRasterExtent(re, LatLng, srcCrs)

    val requestedZoom =
      if (zooms.length == 1)
        zooms(0)
      else {
        val dw = allMD.map{ case (z, md) => (z, md.cellwidth - srcRE.cellwidth) }
        val dh = allMD.map{ case (z, md) => (z, md.cellheight - srcRE.cellheight) }

        // Search for pyramid resolution that is lowest zoom level with finer resolution than request.
        // In case of failure, requested resolution was finer than finest pyramid level; return the
        // pyramid base.
        math.max(dw.find{ case (_, d) => d < 0 }.map(_._1).getOrElse( allMD.last._1 ),
                 dh.find{ case (_, d) => d < 0 }.map(_._1).getOrElse( allMD.last._1 ))
      }

    val metadata = allMD.find{ x => x._1 == requestedZoom }.get._2
    val crs = metadata.crs
    val gridBounds = metadata.layout.mapTransform(srcRE.extent)

    println(s"Request: zoom=$requestedZoom $gridBounds (Query: ${srcRE.cellSize}, Catalog: ${metadata.cellSize})")

    val query = new LayerQuery[SpatialKey, TileLayerMetadata[SpatialKey]].where(Intersects(gridBounds))
    val layerId = LayerId(params.identifier, requestedZoom)

    val header = altAttributeStore.readHeader[LayerHeader](layerId)
    val read = header.valueClass match {
      case "geotrellis.raster.Tile" =>
        readSinglebandTile _
      case "geotrellis.raster.MultibandTile" =>
        readMultibandTile _
    }

    if (gridBounds.size < 4)
      read(layerId, gridBounds, srcCrs, re)
    else
      read(layerId, gridBounds, srcCrs, re)
  }

  def readSinglebandTile(layerId: LayerId, gb: GridBounds, srcCrs: CRS, targetRaster: RasterExtent): Array[Byte] = {
    val raster = collectionReader
      .query[SpatialKey, Tile, TileLayerMetadata[SpatialKey]](layerId)
      .where(Intersects(gb))
      .result
      .stitch
      .reproject(srcCrs, LatLng, options=Reproject.Options(targetRasterExtent=Some(targetRaster)))

    GeoTiff(raster, LatLng).toByteArray
  }

  def readMultibandTile(layerId: LayerId, gb: GridBounds, srcCrs: CRS, targetRaster: RasterExtent): Array[Byte] = {
    val raster = collectionReader
      .query[SpatialKey, MultibandTile, TileLayerMetadata[SpatialKey]](layerId)
      .where(Intersects(gb))
      .result
      .stitch
      .reproject(srcCrs, LatLng, options=Reproject.Options(targetRasterExtent=Some(targetRaster)))

    GeoTiff(raster, LatLng).toByteArray
  }

}
