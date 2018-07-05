package geotrellis.server.http4s

import geotrellis.server.http4s.auth._
import geotrellis.server.http4s.wcs.WcsService
import geotrellis.server.http4s.cog.CogService
import geotrellis.server.http4s.maml.MamlPersistenceService
import geotrellis.server.core.persistence._

import com.azavea.maml.ast.Expression
import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap
import cats.data._
import cats.effect._
import io.circe._
import io.circe.syntax._
import fs2._
import fs2.StreamApp.ExitCode
import org.http4s.circe._
import org.http4s._
import org.http4s.dsl.Http4sDsl
import org.http4s.client.blaze.Http1Client
import org.http4s.server.blaze.BlazeBuilder
import org.http4s.server.{AuthMiddleware, HttpMiddleware}
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


object Server extends StreamApp[IO] with LazyLogging with Http4sDsl[IO] {

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

  val onFailure: AuthedService[String, IO] =
    Kleisli(req => OptionT.liftF(Forbidden(req.authInfo)))

  private val authMiddleware =
    AuthMiddleware(AuthenticationBackends.fromAuthHeader, onFailure)

  def stream(args: List[String], requestShutdown: IO[Unit]): Stream[IO, ExitCode] = {
    for {
      config     <- Stream.eval(Config.load())
      authM       = AuthMiddleware(AuthenticationBackends.fromConfig(config))
      client     <- Http1Client.stream[IO]().map(KamonClientSupport(_))
      _          <- Stream.eval(IO.pure(logger.info(s"Initializing server at ${config.http.interface}:${config.http.port}")))
      cog         = new CogService
      wcs         = new WcsService(config.catalog.uri)
      mamlPersistence = {
        val hashmapStore = new ConcurrentLinkedHashMap.Builder[UUID, Expression]()
          .maximumWeightedCapacity(1000)
          .build();

        new MamlPersistenceService(hashmapStore)
      }
      pingpong = new PingPongService
      _          <- Stream.eval(IO { Kamon.addReporter(new PrometheusReporter()) })
      exitCode   <- BlazeBuilder[IO]
        .enableHttp2(true)
        .bindHttp(config.http.port, config.http.interface)
        .mountService(commonMiddleware(authM(pingpong.routes)), "/ping")
        .mountService(commonMiddleware(authM(wcs.routes)), "/wcs")
        .mountService(commonMiddleware(authM(cog.routes)), "/cog")
        .mountService(commonMiddleware(authM(mamlPersistence.routes)), "/maml")
        .serve
    } yield exitCode
  }
}

