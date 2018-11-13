package geotrellis.server.wcs

import java.net.URI

case class WcsConf(http: WcsConf.Http, auth: WcsConf.Auth, settings: WcsConf.Settings)

object WcsConf {
  case class Http(interface: String, port: Int)
  case class Auth(signingKey: String)
  case class Settings(catalog: String)
}
