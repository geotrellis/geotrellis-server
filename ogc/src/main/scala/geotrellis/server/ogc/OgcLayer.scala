package geotrellis.server.ogc

import geotrellis.server._
import geotrellis.server.ogc.wms._
import geotrellis.server.ExtentReification.ops._

import geotrellis.contrib.vlm._
import geotrellis.raster.{CellSize, RasterExtent}
import geotrellis.vector.Extent
import geotrellis.proj4.CRS
import com.azavea.maml.ast._
import cats.effect._
import cats.implicits._

/** Layer instances are sufficent to produce displayed the end product of 'get map'
 *  requests. They are produced in [[RasterSourcesModel]] from a combination of a [[GetMap]]
 *  and an instance of [[Source]]
 */
trait OgcLayer {
  def name: String
  def title: String
  def crs: CRS
  def style: Option[StyleModel]
}

case class SimpleLayer(
  name: String,
  title: String,
  crs: CRS,
  source: RasterSource,
  style: Option[StyleModel]
) extends OgcLayer

object SimpleLayer {
  implicit val mapAlgebraLayerReification = new ExtentReification[SimpleLayer] {
    def kind(self: SimpleLayer): MamlKind = MamlKind.Image
    def extentReification(self: SimpleLayer)(implicit contextShift: ContextShift[IO]): (Extent, CellSize) => IO[Literal] =
      (extent: Extent, cs: CellSize) =>  IO {
        self.source.reprojectToGrid(self.crs, RasterExtent(extent, cs)).read(extent).map(RasterLit(_)).get
      }
  }
}

case class MapAlgebraLayer(
  name: String,
  title: String,
  crs: CRS,
  parameters: Map[String, SimpleLayer],
  algebra: Expression,
  style: Option[StyleModel]
) extends OgcLayer
