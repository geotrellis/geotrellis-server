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
import fs2._
import org.http4s._
import org.http4s.server._
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.server.middleware.{CORS, CORSConfig}
import org.http4s.syntax.kleisli._
import com.typesafe.scalalogging.LazyLogging
import pureconfig._

import scala.concurrent.duration._
import java.net.URI

object Server extends LazyLogging with IOApp {
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

  val stream: Stream[IO, ExitCode] = {
    import Conf._
    for {
      conf       <- Stream.eval(LoadConf().as[Conf])
      _          <- Stream.eval(IO.pure(logger.info(s"Advertising service URL at ${conf.serviceUrl("/wms")}")))
      _          <- Stream.eval(IO.pure(logger.info(s"Advertising service URL at ${conf.serviceUrl("/wcs")}")))
      _          <- Stream.eval(IO.pure(logger.info(s"Advertising service URL at ${conf.serviceUrl("/wmts")}")))

      simpleSources = conf.layers.values.collect { case ssc@SimpleSourceConf(_, _, _, _) => ssc.model }.toList
      wmsModel = RasterSourcesModel(conf.wms.layerSources(simpleSources))
      wmtsModel = RasterSourcesModel(conf.wmts.layerSources(simpleSources))
      wcsModel = RasterSourcesModel(conf.wcs.layerSources(simpleSources))

      tileMatrixSetModel = TileMatrixModel(conf.wmts.tileMatrixSets)

      wmsService = new WmsService(wmsModel, conf.serviceUrl("/wms"), conf.wms.serviceMetadata)
      wcsService = new WcsService(wcsModel, conf.serviceUrl("/wcs"), conf.wcs.serviceMetadata)
      wmtsService = new WmtsService(wmtsModel, tileMatrixSetModel, conf.serviceUrl("/wmts"), conf.wmts.serviceMetadata)

      exitCode   <- BlazeServerBuilder[IO]
        .withIdleTimeout(Duration.Inf) // for test purposes only
        .enableHttp2(true)
        .bindHttp(conf.http.port, conf.http.interface)
        .withHttpApp(Router(
          "/wms" -> commonMiddleware(wmsService.routes),
          "/wcs" -> commonMiddleware(wcsService.routes),
          "/wmts" -> commonMiddleware(wmtsService.routes)
        ).orNotFound)
        .serve
    } yield exitCode
  }

  /** The 'main' method for a cats-effect IOApp */
  override def run(args: List[String]): IO[ExitCode] =
    stream.compile.drain.as(ExitCode.Success)
}
