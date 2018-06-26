package geotrellis.server.http4s

import cats.effect.IO
import com.typesafe.config.ConfigFactory
import pureconfig.error.ConfigReaderException

import java.net.URI


case class Config(http: Config.Http, catalog: Config.Catalog)

object Config {
  case class Catalog(uri: URI)
  case class Http(interface: String, port: Int)

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
