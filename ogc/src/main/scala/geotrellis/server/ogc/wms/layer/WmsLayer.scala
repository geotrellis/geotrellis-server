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

/** WmsLayer instances are sufficent to produce displayed the end product of a WMS 'get map'
 *  request. They are produced in [[RasterSourcesModel]] from a combination of a [[GetMap]]
 *  and an instance of [[WmsSource]]
 */
trait WmsLayer {
  def style: Option[StyleModel]
}

