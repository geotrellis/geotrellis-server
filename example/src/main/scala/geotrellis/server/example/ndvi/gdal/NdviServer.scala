package geotrellis.server.example.ndvi.gdal

import java.net.URI

import geotrellis.server._
import geotrellis.server.example._
import geotrellis.server.cog.CogNode
import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap
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

import geotrellis.server.gdal._

import scala.concurrent.duration._
import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global


object NdviServer extends LazyLogging with IOApp {

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

  val fst = new GDALNode(new URI("/Users/daunnc/subversions/git/github/geotrellis-landsat-tutorial/data/r-g-nir.tif"), 1, None).asJson
  val snd = new GDALNode(new URI("/Users/daunnc/subversions/git/github/geotrellis-landsat-tutorial/data/r-g-nir.tif"), 2, None).asJson

  val stream: Stream[IO, ExitCode] = {
    for {
      conf       <- Stream.eval(LoadConf().as[ExampleConf])
      _          <- Stream.eval(IO.pure(logger.info(s"Initializing NDVI service at ${conf.http.interface}:${conf.http.port}/")))
      mamlNdviRendering = new NdviService[GDALNode]()
      exitCode   <- BlazeServerBuilder[IO]
        .enableHttp2(true)
        .bindHttp(conf.http.port, conf.http.interface)
        .withHttpApp(Router("/" -> commonMiddleware(mamlNdviRendering.routes)).orNotFound)
        .serve
    } yield exitCode
  }

  /** The 'main' method for a cats-effect IOApp */
  override def run(args: List[String]): IO[ExitCode] =
    stream.compile.drain.as(ExitCode.Success)
}

