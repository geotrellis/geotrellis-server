package geotrellis.server.wcs.ops

import geotrellis.server.wcs.params.DescribeCoverageWcsParams

import geotrellis.proj4._
import geotrellis.raster._
import geotrellis.raster.reproject.ReprojectRasterExtent
import geotrellis.spark._
import geotrellis.spark.io._
import geotrellis.spark.io.json._
import com.typesafe.scalalogging.LazyLogging

import scala.xml._

trait DescribeCoverage extends LazyLogging {
  def build(metadata: MetadataCatalog, params: DescribeCoverageWcsParams): Elem
}
