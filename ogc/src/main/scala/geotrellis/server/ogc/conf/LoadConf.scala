package geotrellis.server.ogc.conf

import cats.effect.IO
import com.typesafe.config.ConfigFactory
import pureconfig.error.ConfigReaderException

import scala.reflect.ClassTag

object LoadConf {

  import pureconfig._

  def apply(configFile: String = "application.conf") = new {
    def as[Conf: ClassTag: ConfigReader]: IO[Conf] =
      IO {
        loadConfig[Conf](ConfigFactory.load(configFile))
      }.flatMap {
        case Left(e) => IO.raiseError[Conf](new ConfigReaderException[Conf](e))
        case Right(config) => IO.pure(config)
      }
  }
}

