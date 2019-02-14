package geotrellis.server.ogc.wms.source

import geotrellis.server._
import geotrellis.server.ogc.wms._
import geotrellis.server.ExtentReification.ops._
import geotrellis.proj4.CRS

import geotrellis.contrib.vlm._
import geotrellis.raster.CellSize
import geotrellis.vector.Extent
import com.azavea.maml.ast._
import cats.effect._
import cats.implicits._
import opengis.wms._

trait WmsSource {
  def name: String
  def styles: List[StyleModel]
  def crs: CRS
  def bboxIn(crs: CRS): BoundingBox
}

