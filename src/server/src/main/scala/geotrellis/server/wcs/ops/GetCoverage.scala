package geotrellis.server.wcs.ops

import geotrellis.proj4._
import geotrellis.raster._
import geotrellis.raster.crop._
import geotrellis.raster.reproject._
import geotrellis.raster.io.geotiff._
import geotrellis.server.wcs.WcsRoute
import geotrellis.server.wcs.params.GetCoverageWCSParams
import geotrellis.spark._
import geotrellis.spark.io._
import geotrellis.spark.stitch._

import com.typesafe.config.ConfigFactory
import com.github.blemale.scaffeine.{Cache, Scaffeine}

import scala.util.Try
import scala.concurrent.duration._

object GetCoverage {
  /*
  QGIS appears to sample WCS service by placing low and high resolution requests at coverage center. 
  These sampling requests happen for every actual WCS request, we can get really great cache hit rates.
  */
  val requestCache: Cache[(LayerId, GridBounds, RasterExtent), Raster[Tile]] =
      Scaffeine()
        .recordStats()
        .expireAfterWrite(1.hour)
        .maximumSize(32)
        .build()

  /*
  CollectionReader holds AttributeStore which has request cache.
  For weirdness with frequently changing layers inquire within.
  */
  lazy val collectionReader = Try(ConfigFactory.load().getString("server.catalog")).toOption match {
    case Some(uri) => 
      CollectionLayerReader(uri)
    case None => 
      throw new IllegalArgumentException("""Must specify a value for "server.catalog" in application.conf""")
  }

  def build(catalog: WcsRoute.MetadataCatalog, params: GetCoverageWCSParams): Array[Byte] = {
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

    def regionTile(): Raster[Tile] = {
      collectionReader
        .read[SpatialKey, Tile, TileLayerMetadata[SpatialKey]](layerId, query)
        .stitch
        .reproject(srcCrs, LatLng, options=Reproject.Options(targetRasterExtent=Some(re)))
    }

    val responseRaster: Raster[Tile] = 
      if (gridBounds.sizeLong < 4)
        requestCache.get((layerId, gridBounds, re), _ => regionTile())
      else 
        regionTile()

    GeoTiff(responseRaster, LatLng).toByteArray
  }
}
