package geotrellis.server.ogc.wms

import geotrellis.server.ogc.conf._

import cats.effect._
import cats.implicits._
import fs2._
import org.http4s._
import org.http4s.server._
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.server.middleware.{CORS, CORSConfig}
import org.http4s.syntax.kleisli._
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.duration._
import java.net.URI

object WmsServer extends LazyLogging with IOApp {
  val catalogURI: URI = new URI("s3://geotrellis-test/daunnc/LC_TEST")

  private val commonMiddleware: HttpMiddleware[IO] = { (routes: HttpRoutes[IO]) =>
    CORS(routes)
  }

  val stream: Stream[IO, ExitCode] = {
    for {
      conf       <- Stream.eval(LoadConf().as[Conf])
      _          <- Stream.eval(IO.pure(logger.info(s"Initializing WMS service at ${conf.http.interface}:${conf.http.port}/")))
      wcsService = new WmsService(catalogURI, s"${conf.http.interface}", conf.http.port)
      exitCode   <- BlazeServerBuilder[IO]
        .withIdleTimeout(Duration.Inf) // for test purposes only
        .enableHttp2(true)
        .bindHttp(conf.http.port, conf.http.interface)
        .withHttpApp(Router("/" -> commonMiddleware(wcsService.routes)).orNotFound)
        .serve
    } yield exitCode
  }

  /** The 'main' method for a cats-effect IOApp */
  override def run(args: List[String]): IO[ExitCode] =
    stream.compile.drain.as(ExitCode.Success)
}
