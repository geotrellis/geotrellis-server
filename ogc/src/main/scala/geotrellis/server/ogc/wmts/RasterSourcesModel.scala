package geotrellis.server.ogc.wmts

import geotrellis.contrib.vlm.RasterSource
import geotrellis.spark._
import geotrellis.raster._
import geotrellis.server.ogc.wms.WmsParams.GetMap
import geotrellis.server.ogc.conf.Conf

case class RasterSourcesModel(map: Map[String, RasterSource]) {
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