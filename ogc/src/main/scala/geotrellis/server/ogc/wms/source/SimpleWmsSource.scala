package geotrellis.server.ogc.wms.source

import geotrellis.server.ogc.wms._
import geotrellis.server._

import geotrellis.server.ogc.wms
import geotrellis.contrib.vlm._
import geotrellis.raster.CellSize
import geotrellis.vector.Extent
import geotrellis.proj4.CRS
import com.azavea.maml.ast._
import cats.effect._
import cats.implicits._

case class SimpleWmsSource(
  name: String,
  title: String,
  source: RasterSource,
  styles: List[StyleModel]
) extends WmsSource {

  def bboxIn(crs: CRS) = {
    val reprojected = source.reproject(crs)
    CapabilitiesView.boundingBox(crs, reprojected.extent, reprojected.cellSize)
  }
}

