/*
 * Copyright 2020 Azavea
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package geotrellis.server.example.ndvi

import geotrellis.server.example._
import geotrellis.server.vlm.geotiff.GeoTiffNode

import cats.effect._
import cats.implicits._
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import org.http4s._
import org.http4s.server._
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.server.middleware.{CORS, CORSConfig}
import org.http4s.syntax.kleisli._
import pureconfig.generic.auto._
import com.azavea.maml.eval._

import scala.concurrent.duration._

object NdviServer extends IOApp {

  implicit val logger = Slf4jLogger.getLogger[IO]

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

  val createServer: Resource[IO, Server[IO]] = {
    for {
      conf             <- ExampleConf.loadResourceF[IO](None)
      _                <- Resource.liftF {
                            logger.info(
                              s"Initializing NDVI service at ${conf.http.interface}:${conf.http.port}/"
                            )
                          }
      mamlNdviRendering = new NdviService[IO, GeoTiffNode](
                            ConcurrentInterpreter.DEFAULT
                          )
      server           <- BlazeServerBuilder[IO]
                            .enableHttp2(true)
                            .bindHttp(conf.http.port, conf.http.interface)
                            .withHttpApp(
                              Router("/" -> commonMiddleware(mamlNdviRendering.routes)).orNotFound
                            )
                            .resource
    } yield server
  }

  /** The 'main' method for a cats-effect IOApp */
  override def run(args: List[String]): IO[ExitCode] =
    createServer.use(_ => IO.never).as(ExitCode.Success)
}
