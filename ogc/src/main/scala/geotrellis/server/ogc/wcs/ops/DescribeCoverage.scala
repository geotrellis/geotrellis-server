package geotrellis.server.ogc.wcs.ops

import geotrellis.server.ogc._
import geotrellis.server.ogc.wcs.params.DescribeCoverageWcsParams

import geotrellis.proj4._
import geotrellis.raster._
import geotrellis.raster.reproject.ReprojectRasterExtent
import geotrellis.spark._
import geotrellis.spark.io._
import geotrellis.spark.io.json._
import com.typesafe.scalalogging.LazyLogging

import scala.xml._

trait DescribeCoverage {
  def build(rsm: RasterSourcesModel, params: DescribeCoverageWcsParams): Elem
}
