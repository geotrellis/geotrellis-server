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
import scala.concurrent.duration._
import java.net.URI
import pureconfig._

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
      _          <- Stream.eval(IO.pure(logger.info(s"Advertising service URL at ${conf.serviceUrlWms}")))
      _          <- Stream.eval(IO.pure(logger.info(s"Advertising service URL at ${conf.serviceUrlWcs}")))
      simpleLayers = conf.layers.collect { case ssc@SimpleSourceConf(_, _, _, _) => ssc.model }
      mapAlgebraLayers = conf.layers.collect { case mal@MapAlgebraSourceConf(_, _, _, _) => mal.model(simpleLayers) }
      rasterSourcesModel = RasterSourcesModel(simpleLayers ++ mapAlgebraLayers)

      // TODO: Make this come from config instead of being hardcoded
      worldExtent = Extent(-180.0000, -90.0000, 180.0000, 90.0000)
      tileLayout = TileLayout(layoutCols = 360, layoutRows = 180, tileCols = 256, tileRows = 256)
      layoutDefinition = LayoutDefinition(worldExtent, tileLayout)
      tileMatrix = TileMatrix("Title", "Abstract", "ID", worldExtent, tileLayout)
      tileMatrixSet = TileMatrixSet(LatLng, "Title", "Abstract", "ID", List(tileMatrix))
      tileMatrixSetModel = TileMatrixModel(List(tileMatrixSet))
      wmsService = new WmsService(rasterSourcesModel, conf.serviceUrlWms, conf.wms.serviceMetadata)
      wcsService = new WcsService(rasterSourcesModel, conf.serviceUrlWcs)
      wmtsService = new WmtsService(rasterSourcesModel, tileMatrixSetModel, conf.serviceUrlWmts)

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
