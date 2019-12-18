package geotrellis.server.example.overlay

import geotrellis.server.example._
import geotrellis.server._

import cats.data._
import cats.effect._
import cats.implicits._
import fs2._
import com.typesafe.scalalogging.LazyLogging
import org.http4s._
import org.http4s.server._
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.server.middleware.{CORS, CORSConfig}
import org.http4s.syntax.kleisli._
import pureconfig.generic.auto._
import com.azavea.maml.eval._

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global



object WeightedOverlayServer extends LazyLogging with IOApp {

  private val corsConfig = CORSConfig(
    anyOrigin = true,
    anyMethod = false,
    allowedMethods = Some(Set("GET")),
    allowCredentials = true,
    maxAge = 1.day.toSeconds
  )

  private val commonMiddleware: HttpMiddleware[IO] = { (routes: HttpRoutes[IO]) =>
    CORS(routes)
  }

  def stream: Stream[IO, ExitCode] = {
    for {
      conf       <- Stream.eval(LoadConf().as[ExampleConf])
      _          <- Stream.eval(IO { logger.info(s"Initializing Weighted Overlay at ${conf.http.interface}:${conf.http.port}/") })
      overlayService = new WeightedOverlayService(ConcurrentInterpreter.DEFAULT)
      exitCode   <- BlazeServerBuilder[IO]
        .enableHttp2(true)
        .bindHttp(conf.http.port, conf.http.interface)
        .withHttpApp(Router("/" -> commonMiddleware(overlayService.routes)).orNotFound)
        .serve
    } yield exitCode
  }

  /** The 'main' method for a cats-effect IOApp */
  override def run(args: List[String]): IO[ExitCode] =
    stream.compile.drain.as(ExitCode.Success)
}
