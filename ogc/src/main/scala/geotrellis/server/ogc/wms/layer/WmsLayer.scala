package geotrellis.server.ogc.wms.layer

import geotrellis.server._
import geotrellis.server.ogc.wms._
import geotrellis.server.ExtentReification.ops._

import geotrellis.contrib.vlm._
import geotrellis.raster.CellSize
import geotrellis.vector.Extent
import com.azavea.maml.ast._
import cats.effect._
import cats.implicits._

trait WmsLayer {
  def style: Option[StyleModel]
}

