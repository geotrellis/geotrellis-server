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

package geotrellis.server.ogc

import geotrellis.server.ogc.conf._
import geotrellis.server.ogc.wms._
import geotrellis.server.ogc.wcs._
import geotrellis.server.ogc.wmts._
import cats.effect._
import cats.implicits._
import com.monovore.decline._
import org.http4s._
import org.http4s.server._
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.server.middleware.CORS
import org.http4s.syntax.kleisli._
import org.backuity.ansi.AnsiFormatter.FormattedHelper
import com.google.common.util.concurrent.ThreadFactoryBuilder
import io.chrisdavenport.log4cats.Logger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext
import java.net.URL
import java.util.concurrent.Executors

object Main
    extends CommandApp(
      name = "java -jar geotrellis-pointcloud-server-assembly.jar",
      header = "Host GT layers through WMS, WMTS, and WCS services",
      main = {
        implicit val logger: Logger[IO] = Slf4jLogger.getLogger

        // TODO: remove it
        val loggerSync = org.log4s.getLogger

        val publicUrlReq =
          Opts.option[String](
            "public-url",
            short = "u",
            help = "Public URL for services to advertise"
          )

        val interfaceOpt = Opts
          .option[String](
            "interface",
            short = "i",
            help = "The interface for this server to bind on"
          )
          .withDefault("0.0.0.0")

        val portOpt = Opts
          .option[Int](
            "port",
            short = "p",
            help = "The port for this server to bind on"
          )
          .withDefault(9000)

        val configPathOpt = Opts
          .option[String](
            "conf",
            short = "c",
            help = "Full path to a HOCON configuration file (https://github.com/lightbend/config/blob/master/HOCON.md)"
          )
          .orNone

        (publicUrlReq, interfaceOpt, portOpt, configPathOpt).mapN { (publicUrl, interface, port, configPath) =>
          {
            loggerSync.info(ansi"%green{Locally binding services to ${interface}:${port}}")
            loggerSync.info(ansi"%green{Advertising services at ${publicUrl}}")
            configPath match {
              case Some(path) => loggerSync.info(ansi"%green{Layer and style configurations loaded from ${path}.}")
              case None       => loggerSync.info(ansi"%red{Warning}: No configuration path provided. Loading defaults.")
            }

            implicit val executionContext: ExecutionContext =
              ExecutionContext.fromExecutor(
                Executors.newCachedThreadPool(
                  new ThreadFactoryBuilder()
                    .setNameFormat("raster-io-%d")
                    .build()
                )
              )
            implicit val cs: ContextShift[IO]               =
              IO.contextShift(executionContext)
            implicit val timer: Timer[IO]                   = IO.timer(executionContext)

            val commonMiddleware: HttpMiddleware[IO] = { (routes: HttpRoutes[IO]) =>
              CORS(routes)
            }

            def logOptState[A](
              opt: Option[A],
              upLog: String,
              downLog: String
            ): IO[Unit] = opt.fold(logger.info(downLog))(_ => logger.info(upLog))

            def createServer: Resource[IO, Server[IO]] =
              for {
                conf         <- Conf.loadResourceF[IO](configPath)
                simpleSources = conf.layers.values.collect { case rsc: RasterSourceConf => rsc.toLayer }.toList
                _            <- Resource.liftF(
                                  logOptState(
                                    conf.wms,
                                    ansi"%green{WMS configuration detected}, starting Web Map Service",
                                    ansi"%red{No WMS configuration detected}, unable to start Web Map Service"
                                  )
                                )
                wmsModel      = conf.wms.map { svc =>
                                  WmsModel[IO](
                                    svc.serviceMetadata,
                                    svc.parentLayerMeta,
                                    svc.layerSources(simpleSources),
                                    ExtendedParameters.extendedParametersBinding
                                  )
                                }
                _            <- Resource.liftF(
                                  logOptState(
                                    conf.wmts,
                                    ansi"%green{WMTS configuration detected}, starting Web Map Tiling Service",
                                    ansi"%red{No WMTS configuration detected}, unable to start Web Map Tiling Service"
                                  )
                                )
                wmtsModel     = conf.wmts.map { svc =>
                                  WmtsModel[IO](
                                    svc.serviceMetadata,
                                    svc.tileMatrixSets,
                                    svc.layerSources(simpleSources)
                                  )
                                }
                _            <- Resource.liftF(
                                  logOptState(
                                    conf.wcs,
                                    ansi"%green{WCS configuration detected}, starting Web Coverage Service",
                                    ansi"%red{No WCS configuration detected}, unable to start Web Coverage Service"
                                  )
                                )
                wcsModel      = conf.wcs.map { svc =>
                                  WcsModel[IO](
                                    svc.serviceMetadata,
                                    svc.layerSources(simpleSources),
                                    svc.supportedProjections,
                                    ExtendedParameters.extendedParametersBinding
                                  )
                                }

                ogcService = new OgcService[IO](
                               wmsModel,
                               wcsModel,
                               wmtsModel,
                               new URL(publicUrl)
                             )
                server    <- BlazeServerBuilder[IO]
                               .withIdleTimeout(Duration.Inf)
                               .withResponseHeaderTimeout(Duration.Inf)
                               .enableHttp2(true)
                               .bindHttp(port, interface)
                               .withHttpApp(
                                 Router(
                                   "/" -> commonMiddleware(ogcService.routes)
                                 ).orNotFound
                               )
                               .resource
              } yield server

            createServer
              .use(_ => IO.never)
              .as(ExitCode.Success)
              .void
              .unsafeRunSync
          }
        }
      }
    )
