package geotrellis.server.ogc.wms.layer

import geotrellis.server.ogc.wms._
import geotrellis.server.ogc.wms
import geotrellis.server._

import geotrellis.contrib.vlm._
import geotrellis.raster.CellSize
import geotrellis.vector.Extent
import geotrellis.proj4.CRS
import com.azavea.maml.ast._
import cats.effect._
import cats.implicits._

case class MapAlgebraWmsLayer(
  name: String,
  title: String,
  crs: CRS,
  parameters: Map[String, SimpleWmsLayer],
  algebra: Expression,
  style: Option[StyleModel]
) extends WmsLayer
