package geotrellis.server.ogc.wcs

import geotrellis.server.ogc._

import geotrellis.proj4._

/** This class holds all the information necessary to construct a response to a WCS request */
case class WcsModel(
  serviceMetadata: ows.ServiceMetadata,
  sources: Seq[OgcSource]
) {

  val sourceLookup: Map[String, OgcSource] = sources.map { layer => layer.name -> layer }.toMap

  /** Take a specific request for a map and combine it with the relevant [[OgcSource]]
   *  to produce a [[Layer]]
   */
  def getLayer(crs: CRS, maybeLayerName: Option[String], maybeStyleName: Option[String]): Option[OgcLayer] = {
    for {
      layerName  <- maybeLayerName
      source <- sourceLookup.get(layerName)
    } yield {
      val styleName: Option[String] = maybeStyleName.orElse(source.styles.headOption.map(_.name))
      val style: Option[OgcStyle] = styleName.flatMap { name => source.styles.find(_.name == name) }
      source match {
        case MapAlgebraSource(name, title, rasterSources, algebra, styles) =>
          val simpleLayers = rasterSources.mapValues { rs =>
            SimpleOgcLayer(name, title, crs, rs, style)
          }
          MapAlgebraOgcLayer(name, title, crs, simpleLayers, algebra, style)
        case SimpleSource(name, title, rasterSource, styles) =>
          SimpleOgcLayer(name, title, crs, rasterSource, style)
      }
    }
  }
}
