package geotrellis.server.ogc.wcs

import geotrellis.proj4._
import geotrellis.raster._
import geotrellis.raster.io.geotiff._
import geotrellis.server._
import geotrellis.server.ogc._
import geotrellis.server.ogc.wcs.params.GetCoverageWcsParams

import com.azavea.maml.error._
import com.azavea.maml.eval._
import cats.data.Validated._
import cats.effect._
import com.github.blemale.scaffeine.{Cache, Scaffeine}
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.duration._

class GetCoverage(wcsModel: WcsModel) extends LazyLogging {

  /**
   * QGIS appears to sample WCS service by placing low and high resolution requests at coverage center.
   * These sampling requests happen for every actual WCS request, we can get really great cache hit rates.
   */
  lazy val requestCache: Cache[GetCoverageWcsParams, Array[Byte]] =
      Scaffeine()
        .recordStats()
        .expireAfterWrite(1.hour)
        .maximumSize(32)
        .build()

  def build(params: GetCoverageWcsParams)(implicit contextShift: ContextShift[IO]): Array[Byte] =
    requestCache.getIfPresent(params) match {
      case Some(bytes) =>
        logger.trace(s"GetCoverage cache HIT: $params")
        bytes
      case None =>
        logger.trace(s"GetCoverage cache MISS: $params")
        val src = wcsModel.sourceLookup(params.identifier)
        val re = RasterExtent(params.boundingBox, params.width, params.height)

        val eval = src match {
          case SimpleSource(name, title, source, styles) =>
            LayerExtent.identity(SimpleOgcLayer(name, title, LatLng, source, None))
          case MapAlgebraSource(name, title, sources, algebra, styles) =>
            val simpleLayers = sources.mapValues { rs => SimpleOgcLayer(name, title, LatLng, rs, None) }
            LayerExtent(IO.pure(algebra), IO.pure(simpleLayers), ConcurrentInterpreter.DEFAULT)
        }

        // TODO: Return IO instead
        eval(re.extent, re.cellSize).unsafeRunSync match {
          case Valid(mbtile) =>
            val bytes = GeoTiff(Raster(mbtile, re.extent), LatLng).toByteArray
            requestCache.put(params, bytes)
            bytes
          case Invalid(errs) =>
            throw new MamlException(errs)
        }
    }
}
