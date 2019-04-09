package geotrellis.server.ogc.wms

import geotrellis.proj4.CRS

/** Parent layer metadata class (used in configuration and reporting capabilities) */
case class WmsParentLayerMeta(
  name: Option[String],
  title: String,
  description: Option[String],
  supportedProjections: List[CRS]
)
