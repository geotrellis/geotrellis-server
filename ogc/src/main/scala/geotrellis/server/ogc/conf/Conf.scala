package geotrellis.server.ogc.conf

case class Conf(http: Conf.Http)

object Conf {
  case class Http(interface: String, port: Int)
}
