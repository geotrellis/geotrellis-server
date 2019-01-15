package geotrellis.server.vlm.gdal

import geotrellis.server._
import geotrellis.server.vlm._
import geotrellis.contrib.vlm.gdal.{GDALBaseRasterSource, GDALRasterSource}
import geotrellis.raster._
import geotrellis.raster.io.geotiff.AutoHigherResolution
import geotrellis.contrib.vlm.TargetRegion
import geotrellis.raster.resample.NearestNeighbor
import geotrellis.vector.Extent

import com.azavea.maml.ast.{Literal, MamlKind, RasterLit}
import _root_.io.circe._
import _root_.io.circe.generic.semiauto._
import cats.implicits._
import cats.effect._
import cats.data.{NonEmptyList => NEL}

import java.net.URI

case class GDALNode(uri: URI, band: Int, celltype: Option[CellType])

object GDALNode extends RasterSourceUtils {
  def getRasterSource(uri: String): GDALBaseRasterSource = GDALRasterSource(uri)

  implicit val gdalNodeEncoder: Encoder[GDALNode] = deriveEncoder[GDALNode]
  implicit val gdalNodeDecoder: Decoder[GDALNode] = deriveDecoder[GDALNode]

  implicit val gdalNodeRasterExtents: HasRasterExtents[GDALNode] = new HasRasterExtents[GDALNode] {
    def rasterExtents[F[_]](self: GDALNode)(implicit F: ConcurrentEffect[F]): F[NEL[RasterExtent]] =
      getRasterExtents(self.uri.toString)
  }

  implicit val gdalNodeTmsReification: TmsReification[GDALNode] = new TmsReification[GDALNode] {
    def kind(self: GDALNode): MamlKind = MamlKind.Image
    def tmsReification[F[_]](self: GDALNode, buffer: Int)(implicit F: ConcurrentEffect[F]): (Int, Int, Int) => F[Literal] =
      (z: Int, x: Int, y: Int) => {
        def fetch(xCoord: Int, yCoord: Int) =
          fetchTile(self.uri.toString, z, xCoord, yCoord)
            .map(_.tile)
            .map(_.band(self.band))

        fetch(x, y).map { tile =>
          val extent = tmsLevels(z).mapTransform.keyToExtent(x, y)
          RasterLit(Raster(MultibandTile(tile), extent))
        }
      }
  }

  implicit val gdalNodeExtentReification: ExtentReification[GDALNode] = new ExtentReification[GDALNode] {
    def kind(self: GDALNode): MamlKind = MamlKind.Image
    def extentReification[F[_]](self: GDALNode)(implicit F: ConcurrentEffect[F]): (Extent, CellSize) => F[Literal] =
      (extent: Extent, cs: CellSize) => F.delay {
        getRasterSource(self.uri.toString)
          .resample(TargetRegion(RasterExtent(extent, cs)), NearestNeighbor, AutoHigherResolution)
          .read(extent, self.band :: Nil)
          .map { RasterLit(_) } match {
            case Some(lit) => lit
            case None => throw new Exception(s"No tile avail for RasterExtent: ${RasterExtent(extent, cs)}")
          }
      }
  }
}
