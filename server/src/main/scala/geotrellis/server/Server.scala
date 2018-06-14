package geotrellis.server

import geotrellis.server.wcs.WcsService
import geotrellis.server.cog.CogService

import cats.effect._
import io.circe._
import io.circe.syntax._
import fs2._
import fs2.StreamApp.ExitCode
import org.http4s.circe._
import org.http4s._
import org.http4s.server.blaze.BlazeBuilder
import org.http4s.server.HttpMiddleware
import org.http4s.server.middleware.{GZip, CORS, CORSConfig}
import org.http4s.headers.{Location, `Content-Type`}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._


object Server extends StreamApp[IO] {

  private val corsConfig = CORSConfig(
    anyOrigin = true,
    anyMethod = false,
    allowedMethods = Some(Set("GET")),
    allowCredentials = true,
    maxAge = 1.day.toSeconds
  )

  private val middleware: HttpMiddleware[IO] = { (routes: HttpService[IO]) =>
    CORS(routes)
  }

  def stream(args: List[String], requestShutdown: IO[Unit]): Stream[IO, ExitCode] = {
    for {
      config     <- Stream.eval(Config.load())
      _          <- Stream.eval(IO { println(s"Serving at ${config.http.interface}:${config.http.port}") })
      cog      = new CogService
      //wcs      = new WcsService(config.catalog.uri)
      pingpong = new PingPongService
      exitCode   <- BlazeBuilder[IO]
        .enableHttp2(true)
        .bindHttp(config.http.port, config.http.interface)
        .mountService(middleware(pingpong.routes), "/ping")
        //.mountService(middleware(wcs.routes), "/wcs")
        .mountService(middleware(cog.routes), "/cog")
        .serve
    } yield exitCode
  }
}

