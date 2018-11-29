package geotrellis.server.example

case class ExampleConf(http: ExampleConf.Http, auth: ExampleConf.Auth)

object ExampleConf {
  case class Http(interface: String, port: Int)
  case class Auth(signingKey: String)
}

