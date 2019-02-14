package geotrellis.server.ogc.wms

import geotrellis.server.ogc.wms.source._
import geotrellis.server.ogc.wms.layer._
import geotrellis.server.ogc.wms.WmsParams.GetMap
import geotrellis.server.ogc.conf._

import geotrellis.contrib.vlm.RasterSource
import geotrellis.contrib.vlm.geotiff._
import geotrellis.contrib.vlm.gdal._
import geotrellis.contrib.vlm.avro._
import geotrellis.spark.tiling._
import geotrellis.spark._
import geotrellis.proj4._
import geotrellis.raster.render.{ColorMap, ColorRamp, Png}
import geotrellis.raster._
import geotrellis.spark.io.s3.AmazonS3Client
import geotrellis.vector.Extent
import opengis.wms._
import scalaxb.CanWriteXML
import com.amazonaws.services.s3.{AmazonS3ClientBuilder, AmazonS3URI}
import java.io.File
import java.net._

import geotrellis.raster.histogram.Histogram
import geotrellis.server.ogc.conf.{Conf}

case class RasterSourcesModel(
  sources: Seq[WmsSource]
) {

  val sourceLookup: Map[String, WmsSource] = sources.map({layer => layer.name -> layer}).toMap

  def getLayer(wmsReq: GetMap): Option[WmsLayer] = {
    for {
      sourceName  <- wmsReq.layers.headOption
      source <- sourceLookup.get(sourceName)
    } yield {
      val re = RasterExtent(wmsReq.boundingBox, wmsReq.width, wmsReq.height)
      val styleName: Option[String] = wmsReq.styles.headOption.orElse(source.styles.headOption.map(_.name))
      val style: Option[StyleModel] = styleName.flatMap { name => source.styles.find(_.name == name) }
      source match {
        case MapAlgebraWmsSource(name, title, rasterSources, algebra, styles) =>
          val simpleLayers = rasterSources.mapValues { rs =>
            SimpleWmsLayer(name, title, wmsReq.crs, rs, style)
          }
          MapAlgebraWmsLayer(name, title, wmsReq.crs, simpleLayers, algebra, style)
        case SimpleWmsSource(name, title, rasterSource, styles) =>
          SimpleWmsLayer(name, title, wmsReq.crs, rasterSource, style)
      }
    }
  }
}
