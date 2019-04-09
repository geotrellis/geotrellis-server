package geotrellis.server.ogc.conf

import cats.effect.IO
import com.typesafe.config.ConfigFactory
import pureconfig.error.ConfigReaderException
import pureconfig._

import java.io.File
import scala.reflect.ClassTag

object LoadConf {
  def apply(configPath: Option[String]) = new {
    def as[Conf: ClassTag: ConfigReader]: IO[Conf] =
      IO {
        configPath match {
          case Some(path) =>
            val configFile = new File(path)
            val parsed = ConfigFactory.parseFile(configFile)
            loadConfig[Conf](ConfigFactory.load(parsed))
          case None =>
            loadConfig[Conf](ConfigFactory.load("application.conf"))
        }
      }.flatMap {
        case Left(e) => IO.raiseError[Conf](new ConfigReaderException[Conf](e))
        case Right(config) => IO.pure(config)
      }
  }
}
