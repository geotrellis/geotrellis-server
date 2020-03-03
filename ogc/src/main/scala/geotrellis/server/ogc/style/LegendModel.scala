package geotrellis.server.ogc.style

case class LegendModel(
  format: String,
  width: Int,
  height: Int,
  onlineResource: OnlineResourceModel
)