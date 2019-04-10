package geotrellis.server.ogc

import geotrellis.proj4.LatLng
import geotrellis.raster.TileLayout
import geotrellis.spark.tiling.LayoutDefinition
import geotrellis.vector.Extent
import geotrellis.server.ogc.conf._
import geotrellis.server.ogc.wms._
import geotrellis.server.ogc.wcs._
import geotrellis.server.ogc.wmts._

import cats.effect._
import cats.implicits._
import com.monovore.decline._
import fs2._
import org.http4s._
import org.http4s.server._
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.server.middleware.{CORS, CORSConfig}
import org.http4s.syntax.kleisli._
import pureconfig._
import org.backuity.ansi.AnsiFormatter.FormattedHelper

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext
import java.net.URL

object Main extends CommandApp(
  name = "java -jar geotrellis-ogc-server.jar",
  header = "Host GT layers through WMS, WMTS, and WCS services",
  main = {
    val publicUrlReq =
      Opts.option[String]("public-url",
                       short = "u",
                       help = "Public URL for services to advertise")

    val interfaceOpt = Opts
      .option[String]("interface",
                   short = "i",
                   help =
                     "The interface for this server to bind on")
      .withDefault("0.0.0.0")

    val portOpt = Opts
      .option[Int]("port",
                   short = "p",
                   help =
                     "The port for this server to bind on")
      .withDefault(9000)

    val configPathOpt = Opts
      .option[String]("conf",
                   short = "c",
                   help = "Full path to a HOCON configuration file (https://github.com/lightbend/config/blob/master/HOCON.md)")
      .orNone

    (publicUrlReq, interfaceOpt, portOpt, configPathOpt).mapN {
      (publicUrl, interface, port, configPath) => {

        println(ansi"%green{Locally binding services to ${interface}:${port}}")
        println(ansi"%green{Advertising services at ${publicUrl}}")
        configPath match {
          case Some(path) =>
            println(ansi"%green{Layer and style configurations loaded from ${path}.}")
          case None =>
            println(ansi"%red{Warning}: No configuration path provided. Loading defaults.")
        }


        implicit val cs: ContextShift[IO] = IO.contextShift(ExecutionContext.global)
        implicit val timer = IO.timer(ExecutionContext.global)

        val corsConfig = CORSConfig(
          anyOrigin = true,
          anyMethod = false,
          allowedMethods = Some(Set("GET")),
          allowCredentials = true,
          maxAge = 1.day.toSeconds
        )

        val commonMiddleware: HttpMiddleware[IO] = { (routes: HttpRoutes[IO]) =>
          CORS(routes)
        }

        val stream: Stream[IO, ExitCode] = {
          import Conf._
          for {
            conf       <- Stream.eval(LoadConf(configPath).as[Conf])
            simpleSources = conf
              .layers
              .values
              .collect { case ssc@SimpleSourceConf(_, _, _, _) => ssc.model }
              .toList
            wmsModel = WmsModel(
              conf.wms.serviceMetadata,
              conf.wms.parentLayerMeta,
              conf.wms.layerSources(simpleSources)
            )
            wmtsModel = WmtsModel(
              conf.wmts.serviceMetadata,
              conf.wmts.tileMatrixSets,
              conf.wmts.layerSources(simpleSources)
            )
            wcsModel = WcsModel(
              conf.wcs.serviceMetadata,
              conf.wcs.layerSources(simpleSources)
            )
            ogcService = new OgcService(wmsModel, wcsModel, wmtsModel, new URL(publicUrl))
            exitCode   <- BlazeServerBuilder[IO]
              .withIdleTimeout(Duration.Inf) // for test purposes only
              .enableHttp2(true)
              .bindHttp(port, interface)
              .withHttpApp(Router(
                "/" -> commonMiddleware(ogcService.routes)
              ).orNotFound)
              .serve
          } yield exitCode
        }

        /** End of the world - wrap up the work defined above and execute it */
        stream
          .compile
          .drain
          .as(ExitCode.Success)
          .unsafeRunSync
      }
    }
  }
)
