package geotrellis.server.ogc.wms

import geotrellis.contrib.vlm.RasterSource
import geotrellis.spark.tiling._
import geotrellis.proj4._
import geotrellis.raster.render.{ColorRamp, Png}
import geotrellis.raster.{MultibandTile, Raster}
import geotrellis.server.ogc.wms.WmsParams.GetMap
import geotrellis.vector.Extent
import opengis.wms._
import scalaxb.CanWriteXML

case class RasterSourcesModel(map: Map[String, RasterSource]) {
  import RasterSourcesModel._

  def getMap(wmsReq: GetMap): Option[Raster[MultibandTile]] =
    map
      .get(wmsReq.identifier)
      .flatMap { rs =>rs.reproject(wmsReq.crs).read(wmsReq.boundingBox) }
      .map { _.resample(wmsReq.width, wmsReq.height) }

  def getMapWithColorRamp(wmsReq: GetMap, colorRamp: Option[ColorRamp] = None, bandIndex: Int = 0): Option[Png] =
    getMap(wmsReq).map { raster =>
      colorRamp
        .map(raster.tile.band(bandIndex).renderPng(_))
        .getOrElse(raster.tile.band(bandIndex).renderPng())
    }

  def extent(crs: CRS = LatLng): Option[Extent] = map.values.foldLeft(Option.empty[Extent]) { case (acc, rs) =>
    acc.map(_.combine(rs.extent.reproject(rs.crs, crs)))
  }

  def toLayer(crs: CRS = LatLng): Layer = {
    Layer(
      Name = Some("GeoTrellis WMS Layer"),
      Title = "GeoTrellis WMS Layer",
      Abstract = Some("GeoTrellis WMS Layer"),
      KeywordList = None,
      // All layers are avail at least at this CRS
      // All sublayers would have metadata in this CRS + its own
      CRS = s"EPSG: ${crs.epsgCode}" :: Nil,
      // Extent of all layers in LatLng
      EX_GeographicBoundingBox = Some(crs.worldExtent).map { case Extent(xmin, ymin, xmax, ymax) =>
        opengis.wms.EX_GeographicBoundingBox(xmin, xmax, ymin, ymax)
      },
      // no bounding box is required for the global layer
      BoundingBox = Nil,
      Dimension = Nil,
      Attribution = None,
      AuthorityURL = Nil,
      Identifier = Nil,
      MetadataURL = Nil,
      DataURL = Nil,
      FeatureListURL = Nil,
      Style = Nil,
      MinScaleDenominator = None,
      MaxScaleDenominator = None,
      Layer = map.map { case (name, rs) => rs.toLayer(name, crs) }.toSeq,
      attributes = Map.empty
    )
  }
}

object RasterSourcesModel {
  implicit def toRecord[T: CanWriteXML](t: T): scalaxb.DataRecord[T] = scalaxb.DataRecord(t)

  implicit class RasterSourceMethods(val self: RasterSource) {
    def toLayer(layerName: String, crs: CRS = LatLng): Layer = {
      Layer(
        Name = Some(layerName),
        Title = layerName,
        Abstract = Some(layerName),
        KeywordList = None,
        // extra CRS that is suppotred by this layer
        CRS = s"EPSG: ${self.crs.epsgCode}" :: Nil,
        // global Extent for the CRS
        EX_GeographicBoundingBox = Some(self.extent.reproject(self.crs, LatLng)).map { case Extent(xmin, ymin, xmax, ymax) =>
          opengis.wms.EX_GeographicBoundingBox(xmin, xmax, ymin, ymax)
        },
        // no bounding box is required for the global layer
        BoundingBox = {
          if(crs != self.crs) {
            List(crs, self.crs).map { crs =>
              val Extent(xmin, ymin, xmax, ymax) = self.extent.reproject(self.crs, crs)
              BoundingBox(Map(
                "@CRS" -> s"EPSG: ${crs.epsgCode}",
                "@minx" -> xmin,
                "@miny" -> ymin,
                "@maxx" -> xmax,
                "@maxy" -> ymax
              ))
            }
          } else {
            val Extent(xmin, ymin, xmax, ymax) = self.extent
            BoundingBox(Map(
              "@CRS" -> s"EPSG: ${crs.epsgCode}",
              "@minx" -> xmin,
              "@miny" -> ymin,
              "@maxx" -> xmax,
              "@maxy" -> ymax
            )) :: Nil
          }
        },
        Dimension = Nil,
        Attribution = None,
        AuthorityURL = Nil,
        Identifier = Nil,
        MetadataURL = Nil,
        DataURL = Nil,
        FeatureListURL = Nil,
        Style = Nil,
        MinScaleDenominator = None,
        MaxScaleDenominator = None,
        Layer = Nil,
        attributes = Map.empty
      )
    }
  }
}
