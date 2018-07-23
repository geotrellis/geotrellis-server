package geotrellis.server.core.conf

import cats.effect.IO
import com.typesafe.config.ConfigFactory
import pureconfig.error.ConfigReaderException

import java.net.URI


case class Config(http: Config.Http, auth: Config.Auth)

object Config {
  case class Http(interface: String, port: Int)
  case class Auth(signingKey: String)

  import pureconfig._

  def load(configFile: String = "application.conf"): IO[Config] = {
    IO {
      loadConfig[Config](ConfigFactory.load(configFile))
    }.flatMap {
      case Left(e) => IO.raiseError[Config](new ConfigReaderException[Config](e))
      case Right(config) => IO.pure(config)
    }
  }
}
