package geotrellis

import cats.effect.IO
import io.chrisdavenport.log4cats.Logger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger

import scala.concurrent.ExecutionContext.Implicits.global

package object server {
  implicit val cs = IO.contextShift(global)

  implicit val logger: Logger[IO] = Slf4jLogger.getLogger
}
