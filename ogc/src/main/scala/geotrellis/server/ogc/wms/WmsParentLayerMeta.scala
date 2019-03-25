package geotrellis.server.ogc.wms

import geotrellis.proj4.CRS

case class WmsParentLayerMeta(
  name: Option[String],
  title: String,
  description: Option[String],
  supportedProjections: List[CRS]
)
