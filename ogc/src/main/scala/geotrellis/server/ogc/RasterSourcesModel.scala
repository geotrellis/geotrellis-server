package geotrellis.server.ogc

import geotrellis.server.ogc.wms.WmsParams.GetMap
import geotrellis.server.ogc.wmts.WmtsParams.GetTile
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
  sources: Seq[OgcSource]
) {

  val sourceLookup: Map[String, OgcSource] = sources.map({layer => layer.name -> layer}).toMap

  /** Take a specific request for a map and combine it with the relevant [[OgcSource]]
   *  to produce a [[Layer]]
   */
  def getLayer(crs: CRS, maybeLayerName: Option[String], maybeStyleName: Option[String]): Option[OgcLayer] = {
    for {
      layerName  <- maybeLayerName
      source <- sourceLookup.get(layerName)
    } yield {
      val styleName: Option[String] = maybeStyleName.orElse(source.styles.headOption.map(_.name))
      val style: Option[StyleModel] = styleName.flatMap { name => source.styles.find(_.name == name) }
      source match {
        case MapAlgebraSource(name, title, rasterSources, algebra, styles) =>
          val simpleLayers = rasterSources.mapValues { rs =>
            SimpleWmsLayer(name, title, crs, rs, style)
          }
          MapAlgebraWmsLayer(name, title, crs, simpleLayers, algebra, style)
        case SimpleSource(name, title, rasterSource, styles) =>
          SimpleWmsLayer(name, title, crs, rasterSource, style)
      }
    }
  }

  def getWmtsLayer(crs: CRS, layerName: String, layout: LayoutDefinition, styleName: String): Option[OgcLayer] = {
    for {
      source <- sourceLookup.get(layerName)
    } yield {
      val style: Option[StyleModel] = source.styles.find(_.name == styleName)
      source match {
        case MapAlgebraSource(name, title, rasterSources, algebra, styles) =>
          val simpleLayers = rasterSources.mapValues { rs =>
            SimpleWmtsLayer(name, title, crs, layout, rs, style)
          }
          MapAlgebraWmtsLayer(name, title, crs, layout, simpleLayers, algebra, style)
        case SimpleSource(name, title, rasterSource, styles) =>
          SimpleWmtsLayer(name, title, crs, layout, rasterSource, style)
      }
    }
  }
}
