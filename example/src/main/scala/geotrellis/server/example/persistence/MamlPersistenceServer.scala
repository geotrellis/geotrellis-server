package geotrellis.server.example.persistence

import geotrellis.server.example.ExampleConf
import geotrellis.server.core.conf.LoadConf
import geotrellis.server.core.maml._

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

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.collection.mutable
import java.util.UUID


object MamlPersistenceServer extends StreamApp[IO] with LazyLogging with Http4sDsl[IO] {

  private val corsConfig = CORSConfig(
    anyOrigin = true,
    anyMethod = false,
    allowedMethods = Some(Set("GET")),
    allowCredentials = true,
    maxAge = 1.day.toSeconds
  )

  private val commonMiddleware: HttpMiddleware[IO] = { (routes: HttpService[IO]) =>
    CORS(routes)
  }

  def stream(args: List[String], requestShutdown: IO[Unit]): Stream[IO, ExitCode] = {
    for {
      conf       <- Stream.eval(LoadConf().as[ExampleConf])
      _          <- Stream.eval(IO.pure(logger.info(s"Initializing Weighted Overlay at ${conf.http.interface}:${conf.http.port}/maml/overlay")))
      // This hashmap has a [MamlStore] implementation
      mamlStore = new ConcurrentLinkedHashMap.Builder[UUID, Expression]()
                    .maximumWeightedCapacity(1000)
                    .build();
      mamlPersistence = new MamlPersistenceService[HashMapMamlStore, CogNode](mamlStore)
      exitCode   <- BlazeBuilder[IO]
        .enableHttp2(true)
        .bindHttp(conf.http.port, conf.http.interface)
        .mountService(commonMiddleware(mamlPersistence.routes), "/maml/persistence")
        .serve
    } yield exitCode
  }
}

