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

import scala.util.Try

object GetCoverage {
  def build(catalog: WcsRoute.MetadataCatalog, params: GetCoverageWCSParams): Array[Byte] = {
    def valueReader = Try(ConfigFactory.load().getString("server.catalog")).toOption match {
      case Some(uri) => ValueReader(uri)
      case None => throw new IllegalArgumentException("""Must specify a value for "server.catalog" in application.conf""")
    }
    def as = valueReader.attributeStore

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

    def reader = valueReader.reader[SpatialKey, Tile](LayerId(params.identifier, requestedZoom))

    val metadata = allMD.find{ x => x._1 == requestedZoom }.get._2
    val crs = metadata.crs
    val maptrans = metadata.layout.mapTransform
    val gridBounds = maptrans(srcRE.extent)

    println(s"Requested zoom level=$requestedZoom (AOI has ${srcRE.cellSize}, target has ${metadata.cellSize})")

    val regionTile = gridBounds.coordsIter.toSeq.flatMap{ case (x, y) => Try(SpatialKey(x, y) -> reader.read(SpatialKey(x, y))).toOption }.stitch
    val region = Raster(regionTile, maptrans(gridBounds)).reproject(srcCrs, LatLng)
    val extract = region.crop(re.extent)

    GeoTiff(extract, LatLng).toByteArray
  }
}
