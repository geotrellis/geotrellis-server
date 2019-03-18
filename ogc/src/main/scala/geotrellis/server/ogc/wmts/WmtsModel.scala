package geotrellis.server.ogc.wmts

import geotrellis.server.ogc._
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

case class WmtsModel(
  serviceMetadata: ows.ServiceMetadata,
  matrices: List[GeotrellisTileMatrixSet],
  sources: Seq[OgcSource]
) {

  val sourceLookup: Map[String, OgcSource] = sources.map({layer => layer.name -> layer}).toMap

  val matrixSetLookup: Map[String, GeotrellisTileMatrixSet] =
    matrices.map({tileMatrixSet => tileMatrixSet.identifier -> tileMatrixSet}).toMap

  /** Take a specific request for a map and combine it with the relevant [[OgcSource]]
   *  to produce a [[TiledOgcLayer]]
   */
  def getLayer(crs: CRS, layerName: String, layout: LayoutDefinition, styleName: String): Option[TiledOgcLayer] = {
    for {
      source <- sourceLookup.get(layerName)
    } yield {
      val style: Option[StyleModel] = source.styles.find(_.name == styleName)
      source match {
        case MapAlgebraSource(name, title, rasterSources, algebra, styles) =>
          val simpleLayers = rasterSources.mapValues { rs =>
            SimpleTiledOgcLayer(name, title, crs, layout, rs, style)
          }
          MapAlgebraTiledOgcLayer(name, title, crs, layout, simpleLayers, algebra, style)
        case SimpleSource(name, title, rasterSource, styles) =>
          SimpleTiledOgcLayer(name, title, crs, layout, rasterSource, style)
      }
    }
  }

  def getMatrixLayoutDefinition(tileMatrixSetId: String, tileMatrixId: String): Option[LayoutDefinition] =
    for {
      matrixSet <- matrixSetLookup.get(tileMatrixSetId)
      matrix <- matrixSet.tileMatrix.find(_.identifier == tileMatrixId)
    } yield matrix.layout

  def getMatrixCrs(tileMatrixSetId: String): Option[CRS] =
    for {
      matrixSet <- matrixSetLookup.get(tileMatrixSetId)
    } yield matrixSet.supportedCrs
}
