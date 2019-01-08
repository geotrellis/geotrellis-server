package geotrellis.server.wms

import java.net.URI

case class WmsConf(http: WmsConf.Http, auth: WmsConf.Auth, settings: WmsConf.Settings)

object WmsConf {
  case class Http(interface: String, port: Int)
  case class Auth(signingKey: String)
  case class Settings(catalog: String)
}
