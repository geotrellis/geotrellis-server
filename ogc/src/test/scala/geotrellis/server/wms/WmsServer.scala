package geotrellis.server.ogc.wms

import geotrellis.server._
import geotrellis.server.example._

import cats.data._
import cats.effect._
import cats.implicits._
import io.circe._
import io.circe.syntax._
import fs2._
import com.typesafe.scalalogging.LazyLogging
import org.http4s.circe._
import org.http4s._
import org.http4s.dsl.Http4sDsl
import org.http4s.server._
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.server.middleware.{CORS, CORSConfig}
import org.http4s.syntax.kleisli._

import java.util.UUID
import scala.concurrent.duration._
import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global

object WmsServer extends LazyLogging with IOApp {

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

  val ip: String = {
    val socket = new java.net.DatagramSocket()
    socket.connect(java.net.InetAddress.getByName("8.8.8.8"), 10002)
    val result = socket.getLocalAddress.getHostAddress
    socket.close
    result
  }

  val stream: Stream[IO, ExitCode] = {
    for {
      conf       <- Stream.eval(LoadConf().as[WmsConf])
      _          <- Stream.eval(IO.pure(logger.info(s"Initializing WMS service at ${conf.http.interface}:${conf.http.port}")))
      wmsRendering = new WmsService(new java.net.URI(conf.settings.catalog), ip, conf.http.port)
      exitCode   <- BlazeServerBuilder[IO]
        .enableHttp2(true)
        .bindHttp(conf.http.port, conf.http.interface)
        .withHttpApp(Router("/" -> commonMiddleware(wmsRendering.routes)).orNotFound)
        .serve
    } yield exitCode
  }

  /** The 'main' method for a cats-effect IOApp */
  override def run(args: List[String]): IO[ExitCode] =
    stream.compile.drain.as(ExitCode.Success)
}
