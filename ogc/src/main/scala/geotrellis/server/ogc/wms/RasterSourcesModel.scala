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
import geotrellis.spark.io.s3.AmazonS3Client
import geotrellis.vector.Extent
import opengis.wms._
import scalaxb.CanWriteXML
import com.amazonaws.services.s3.{AmazonS3ClientBuilder, AmazonS3URI}
import java.io.File
import java.net._

import geotrellis.raster.histogram.Histogram
import geotrellis.server.ogc.conf.{ColorMapStyle, ColorRampStyle, Conf, StyleModel}

// TODO: configure the resampling method for raster source. Common: MAX
case class LayerModel(
  name: String,
  source: RasterSource,
  styles: Map[String, StyleModel] = Map.empty
)

case class RasterSourcesModel(
  map: Map[String, LayerModel],
  styles: Map[String, StyleModel] = Map.empty
) {
  import RasterSourcesModel._

  def getMap(wmsReq: GetMap): Option[Array[Byte]] = {
    val re = RasterExtent(wmsReq.boundingBox, wmsReq.width, wmsReq.height)
    // TODO: don't reproject if we don't have to
    for {
      layerName: String <- wmsReq.layers.headOption
      model: LayerModel <- map.get(layerName)
      source: RasterSource = model.source
      rr = source.reprojectToGrid(wmsReq.crs, re)
      raster <- rr.read(wmsReq.boundingBox)
    } yield {
      println("SEE: "  + wmsReq)
      wmsReq.styles.headOption match {
        case Some(styleName) =>
          model.styles.get(styleName) match {
            case Some(m: ColorMapStyle) =>
              val cm = m.styleColorMap()
              println("STYLE: " + m)
              raster.tile.band(bandIndex = 0).renderPng(cm).bytes

            case Some(m: ColorRampStyle) =>
              // TODO: fetch histogram from layer attribute store
              val hist: Histogram[Double] = ???
              val cm = m.styleColorMap(hist)
              raster.tile.band(bandIndex = 0).renderPng(cm).bytes

            case None =>
              // TODO: log failure to lookup style
              raster.tile.band(bandIndex = 0).renderPng.bytes
          }

        case None =>
          raster.tile.band(bandIndex = 0).renderPng.bytes
      }

    }
  }
}

object RasterSourcesModel {
  /**
    * During during construction the layer model will reconcile its style list vs available styles.
    * @param layers List of layer configurations
    * @param availableStyles Mapping of styles available in current configuration
    * @return
    */
  def fromConf(layers: List[Conf.GeoTrellisLayer], availableStyles: Map[String, StyleModel]): RasterSourcesModel = {
    val sources =
      layers.map {
        case Conf.GeoTrellisLayer(uri, name, zoom, bc, styles) =>
          // TODO: log warning when style is asked for by the layer but can't be looked up
          name -> LayerModel(
            name = name,
            source = GeotrellisRasterSource(uri, LayerId(name, zoom)),
            styles = availableStyles.filter{ case (name, style) => styles.contains(name) }
          )
      }

    RasterSourcesModel(sources.toMap)
  }
}
