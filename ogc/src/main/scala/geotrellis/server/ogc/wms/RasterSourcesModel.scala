package geotrellis.server.ogc.wms

import geotrellis.contrib.vlm.RasterSource
import geotrellis.contrib.vlm.geotiff._
import geotrellis.contrib.vlm.gdal._
import geotrellis.contrib.vlm.avro._
import geotrellis.spark.tiling._
import geotrellis.spark._
import geotrellis.proj4._
import geotrellis.raster.render.{ColorMap, ColorRamp, Png}
import geotrellis.raster._
import geotrellis.server.ogc.wms.WmsParams.GetMap
import geotrellis.server.ogc.conf._
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
  layers: Seq[LayerModel]
) {

  val map: Map[String, LayerModel] = layers.map({layer => layer.name -> layer}).toMap


  private def getLayerColorMap(layer: LayerModel, style: Option[String]): Option[ColorMap] = {
    // TODO: add "default-style" to config file to explicitly select the style
    val styleName: Option[String] = style.orElse(layer.styles.headOption.map(_.name))

    for {
      name <- styleName
      style <- layer.styles.find(_.name == name)
      colorMap <- style.colorMap.orElse {
        for { colorRamp <- style.colorRamp } yield {
          val numStops: Int = style.stops.getOrElse(colorRamp.colors.length)
          val ramp: ColorRamp = colorRamp.stops(numStops)
          // TODO: lookup layer histogram, it should be written to LayerId(layerName, 0) at attribute "histogram"
          // GeotrellisRasterSource would have access to attributeStore, we can match and pick
          // for other raster sources it would have to be sampled, stage 2
          // Note: MAML has utility function to sample histograms from RasterSource
          val hist: Array[Histogram[Double]] = ???

          // we're assuming the layers are single band rasters
          ColorMap.fromQuantileBreaks(hist.head, ramp)
        }
      }
    } yield colorMap
  }

  def getMap(wmsReq: GetMap): Option[Array[Byte]] = {
    val re = RasterExtent(wmsReq.boundingBox, wmsReq.width, wmsReq.height)
    for {
      layerName: String <- wmsReq.layers.headOption
      layerModel: LayerModel <- {
        map.get(layerName)
      }
      rr = {
        layerModel.source.reprojectToGrid(wmsReq.crs, re)
      }
      raster <- rr.read(wmsReq.boundingBox)
    } yield {
      getLayerColorMap(layerModel, wmsReq.styles.headOption) match {
        case Some(colorMap) =>
          raster.tile.band(bandIndex = 0).renderPng(colorMap).bytes
        case None =>
          raster.tile.band(bandIndex = 0).renderPng.bytes
      }
    }
  }
}