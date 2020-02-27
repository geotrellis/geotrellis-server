package geotrellis.server.ogc.style


case class OnlineResourceModel(
  `type`: String,
  href: String,
  role: Option[String] = None,
  title: Option[String] = None,
  show: Option[String] = None,
  actuate: Option[String] = None
)