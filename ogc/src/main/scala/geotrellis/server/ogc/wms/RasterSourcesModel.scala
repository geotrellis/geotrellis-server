package geotrellis.server.ogc.wms

import geotrellis.contrib.vlm.RasterSource
import geotrellis.contrib.vlm.geotiff._
import geotrellis.contrib.vlm.gdal._
import geotrellis.contrib.vlm.avro._
import geotrellis.spark.tiling._
import geotrellis.spark._
import geotrellis.proj4._
import geotrellis.raster.render.{ColorRamp, Png}
import geotrellis.raster._
import geotrellis.server.ogc.wms.WmsParams.GetMap
import geotrellis.spark.io.s3.AmazonS3Client
import geotrellis.vector.Extent
import opengis.wms._
import scalaxb.CanWriteXML
import com.amazonaws.services.s3.{AmazonS3ClientBuilder, AmazonS3URI}
import java.io.File
import java.net._

import geotrellis.server.ogc.conf.Conf

case class RasterSourcesModel(map: Map[String, RasterSource]) {
  import RasterSourcesModel._

  def getMap(wmsReq: GetMap): Option[Raster[MultibandTile]] = {
    val re = RasterExtent(wmsReq.boundingBox, wmsReq.width, wmsReq.height)
    // TODO: don't reproject if we don't have to
    for {
      layerName: String <- wmsReq.layers.headOption
      source: RasterSource <- map.get(layerName)
      rr = source.reprojectToGrid(wmsReq.crs, re)
      raster <- rr.read(wmsReq.boundingBox)
    } yield {
      raster
    }
  }
}

object RasterSourcesModel {
  def fromConf(layers: List[Conf.GeoTrellisLayer]): RasterSourcesModel = {
    val sources =
      layers.map {
        case Conf.GeoTrellisLayer(uri, name, zoom, bc) =>
          name -> geotrellis.contrib.vlm.avro.GeotrellisRasterSource(uri, LayerId(name, zoom))
      }

    RasterSourcesModel(sources.toMap)
  }
}
