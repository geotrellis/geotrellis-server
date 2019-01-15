package geotrellis.server.vlm.geotiff

import geotrellis.server._
import geotrellis.server.vlm._
import geotrellis.contrib.vlm.geotiff._
import geotrellis.contrib.vlm.TargetRegion
import geotrellis.raster._
import geotrellis.raster.resample.NearestNeighbor
import geotrellis.raster.io.geotiff.AutoHigherResolution

import geotrellis.vector.Extent
import com.azavea.maml.ast.{Literal, MamlKind, RasterLit}

import _root_.io.circe._
import _root_.io.circe.generic.semiauto._
import cats._
import cats.effect._
import cats.implicits._
import cats.data.{NonEmptyList => NEL}

import java.net.URI

case class GeoTiffNode(uri: URI, band: Int, celltype: Option[CellType])

object GeoTiffNode extends RasterSourceUtils {
  def getRasterSource(uri: String): GeoTiffRasterSource = GeoTiffRasterSource(uri)

  implicit val cogNodeEncoder: Encoder[GeoTiffNode] = deriveEncoder[GeoTiffNode]
  implicit val cogNodeDecoder: Decoder[GeoTiffNode] = deriveDecoder[GeoTiffNode]

  implicit val cogNodeRasterExtents: HasRasterExtents[GeoTiffNode] = new HasRasterExtents[GeoTiffNode] {
    def rasterExtents[F[_]](self: GeoTiffNode)(implicit F: ConcurrentEffect[F]): F[NEL[RasterExtent]] =
      getRasterExtents(self.uri.toString)
  }

  implicit val cogNodeTmsReification: TmsReification[GeoTiffNode] = new TmsReification[GeoTiffNode] {
    def kind(self: GeoTiffNode): MamlKind = MamlKind.Image
    def tmsReification[F[_]](self: GeoTiffNode, buffer: Int)(implicit F: ConcurrentEffect[F]): (Int, Int, Int) => F[Literal] = (z: Int, x: Int, y: Int) => {
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

  implicit val CogNodeExtentReification: ExtentReification[GeoTiffNode] = new ExtentReification[GeoTiffNode] {
    def kind(self: GeoTiffNode): MamlKind = MamlKind.Image
    def extentReification[F[_]](
      self: GeoTiffNode
    )(implicit F: ConcurrentEffect[F]): (Extent, CellSize) => F[Literal] = (extent: Extent, cs: CellSize) =>
      F.delay {
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

