package geotrellis.server.ogc.wms

import geotrellis.server.ogc._
import geotrellis.proj4._

import opengis.wms._

case class WmsModel(
  serviceMeta: opengis.wms.Service,
  parentLayerMeta: WmsParentLayerMeta,
  sources: Seq[OgcSource]
) {

  val sourceLookup: Map[String, OgcSource] = sources.map({layer => layer.name -> layer}).toMap

  /** Take a specific request for a map and combine it with the relevant [[OgcSource]]
   *  to produce a [[Layer]]
   */
  def getLayer(crs: Option[CRS], maybeLayerName: Option[String], maybeStyleName: Option[String]): Option[OgcLayer] = {
    for {
      layerName  <- maybeLayerName
      source <- sourceLookup.get(layerName)
    } yield {
      val styleName: Option[String] = maybeStyleName.orElse(source.styles.headOption.map(_.name))
      val style: Option[OgcStyle] = styleName.flatMap { name => source.styles.find(_.name == name) }
      source match {
        case mas@MapAlgebraSource(name, title, rasterSources, algebra, styles) =>
          val outputCrs = crs.getOrElse(mas.nativeCrs.head)
          val simpleLayers = rasterSources.mapValues { rs =>
            SimpleOgcLayer(name, title, outputCrs, rs, style)
          }
          MapAlgebraOgcLayer(name, title, outputCrs, simpleLayers, algebra, style)
        case ss@SimpleSource(name, title, rasterSource, styles) =>
          SimpleOgcLayer(name, title, crs.getOrElse(ss.nativeCrs.head), rasterSource, style)
      }
    }
  }
}
