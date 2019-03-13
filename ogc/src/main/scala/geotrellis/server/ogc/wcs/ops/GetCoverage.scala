package geotrellis.server.ogc.wcs.ops

import geotrellis.server._
import geotrellis.server.ogc._
import geotrellis.server.ogc.wcs.params.GetCoverageWcsParams

import com.azavea.maml.eval.Interpreter
import com.azavea.maml.error._
import geotrellis.proj4._
import geotrellis.raster._
import geotrellis.raster.crop._
import geotrellis.raster.reproject._
import geotrellis.raster.io.geotiff._
import geotrellis.spark._
import geotrellis.spark.io._
import geotrellis.spark.stitch._
import com.typesafe.scalalogging.LazyLogging

import com.typesafe.config.ConfigFactory
import com.github.blemale.scaffeine.{Cache, Scaffeine}
import cats._
import cats.implicits._
import cats.effect._
import cats.data.Validated._

import scala.util.Try
import scala.concurrent.duration._

class GetCoverage(rsm: RasterSourcesModel) extends LazyLogging {

  /*
  QGIS appears to sample WCS service by placing low and high resolution requests at coverage center.
  These sampling requests happen for every actual WCS request, we can get really great cache hit rates.
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
        val src = rsm.sourceLookup(params.identifier)
        val re = RasterExtent(params.boundingBox, params.width, params.height)

        val eval = src match {
          case SimpleSource(name, title, source, styles) =>
            LayerExtent.identity(SimpleWmsLayer(name, title, LatLng, source, None))
          case MapAlgebraSource(name, title, sources, algebra, styles) =>
            val simpleLayers = sources.mapValues { rs => SimpleWmsLayer(name, title, LatLng, rs, None) }
            LayerExtent(IO.pure(algebra), IO.pure(simpleLayers), Interpreter.DEFAULT)
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
