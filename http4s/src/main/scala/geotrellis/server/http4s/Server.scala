package geotrellis.server.http4s

import geotrellis.server.http4s.wcs.WcsService
import geotrellis.server.http4s.cog.CogService
import geotrellis.server.http4s.maml.{MamlPersistenceService, MamlTmsService}
import geotrellis.server.core.persistence._

import com.azavea.maml.ast.Expression
import com.azavea.maml.eval.BufferingInterpreter
import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap
import cats.effect._
import io.circe._
import io.circe.syntax._
import fs2._
import fs2.StreamApp.ExitCode
import org.http4s.circe._
import org.http4s._
import org.http4s.client.blaze.Http1Client
import org.http4s.server.blaze.BlazeBuilder
import org.http4s.server.HttpMiddleware
import org.http4s.server.middleware.{GZip, CORS, CORSConfig}
import org.http4s.headers.{Location, `Content-Type`}
import org.http4s.client.Client
import com.typesafe.scalalogging.LazyLogging
import kamon.http4s.middleware.server.{KamonSupport => KamonServerSupport}
import kamon.http4s.middleware.client.{KamonSupport => KamonClientSupport}
import kamon.Kamon
import kamon.prometheus.PrometheusReporter

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.collection.mutable
import java.util.UUID


object Server extends StreamApp[IO] with LazyLogging {

  private val corsConfig = CORSConfig(
    anyOrigin = true,
    anyMethod = false,
    allowedMethods = Some(Set("GET")),
    allowCredentials = true,
    maxAge = 1.day.toSeconds
  )

  private val commonMiddleware: HttpMiddleware[IO] = { (routes: HttpService[IO]) =>
    CORS(routes)
  }.compose { (routes: HttpService[IO]) =>
    KamonServerSupport(routes)
  }

  def stream(args: List[String], requestShutdown: IO[Unit]): Stream[IO, ExitCode] = {
    for {
      config     <- Stream.eval(Config.load())
      client     <- Http1Client.stream[IO]().map(KamonClientSupport(_))
      _          <- Stream.eval(IO.pure(logger.info(s"Initializing server at ${config.http.interface}:${config.http.port}")))
      cog         = new CogService
      wcs         = new WcsService(config.catalog.uri)
      pingpong    = new PingPongService
      mamlStore   = new ConcurrentLinkedHashMap.Builder[UUID, Expression]()
        .maximumWeightedCapacity(1000)
        .build();
      mamlPersistence = new MamlPersistenceService(mamlStore)
      maml        = new MamlTmsService(mamlStore, BufferingInterpreter.DEFAULT)
      _          <- Stream.eval(IO { Kamon.addReporter(new PrometheusReporter()) })
      exitCode   <- BlazeBuilder[IO]
        .enableHttp2(true)
        .bindHttp(config.http.port, config.http.interface)
        .mountService(commonMiddleware(pingpong.routes), "/ping")
        .mountService(commonMiddleware(wcs.routes), "/wcs")
        .mountService(commonMiddleware(cog.routes), "/cog")
        .mountService(commonMiddleware(mamlPersistence.routes), "/maml/expression")
        .mountService(commonMiddleware(maml.routes), "/maml/tiled")
        .serve
    } yield exitCode
  }
}

