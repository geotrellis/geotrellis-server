package geotrellis.server.gdal

import geotrellis.server._
import geotrellis.server.gdal.util.GDALUtils
import geotrellis.raster._
import geotrellis.proj4.CRS
import com.azavea.maml.ast.{Literal, MamlKind, RasterLit}

import _root_.io.circe._
import _root_.io.circe.generic.semiauto._
import cats.effect._
import cats.data.{NonEmptyList => NEL}

import java.net.URI

case class GDALNode(uri: URI, band: Int, celltype: Option[CellType])

object GDALNode {
  implicit val cellTypeEncoder: Encoder[CellType] = Encoder.encodeString.contramap[CellType](CellType.toName)
  implicit val cellTypeDecoder: Decoder[CellType] = Decoder[String].emap { name => Right(CellType.fromName(name)) }

  implicit val uriEncoder: Encoder[URI] = Encoder.encodeString.contramap[URI](_.toString)
  implicit val uriDecoder: Decoder[URI] = Decoder[String].emap { str => Right(URI.create(str)) }

  implicit val gdalNodeEncoder: Encoder[GDALNode] = deriveEncoder[GDALNode]
  implicit val gdalNodeDecoder: Decoder[GDALNode] = deriveDecoder[GDALNode]

  implicit val GDALNodeRasterExtents: HasRasterExtents[GDALNode] = new HasRasterExtents[GDALNode] {
    def rasterExtents(self: GDALNode)(implicit contextShift: ContextShift[IO]): IO[NEL[RasterExtent]] =
      GDALUtils.getRasterExtents(self.uri.toString)
    def crs(self: GDALNode)(implicit contextShift: ContextShift[IO]): IO[CRS] = GDALUtils.getCRS(self.uri.toString)
  }

  implicit val GDALNodeTmsReification: TmsReification[GDALNode] = new TmsReification[GDALNode] {
    def kind(self: GDALNode): MamlKind = MamlKind.Image
    def tmsReification(self: GDALNode, buffer: Int)(implicit contextShift: ContextShift[IO]): (Int, Int, Int) => IO[Literal] = (z: Int, x: Int, y: Int) => {
      def fetch(xCoord: Int, yCoord: Int) =
        GDALUtils.fetch(self.uri.toString, z, xCoord, yCoord)
          .map(_.tile)
          .map(_.band(self.band))

      fetch(x, y).map { tile =>
        val extent = GDALUtils.tmsLevels(z).mapTransform.keyToExtent(x, y)
        RasterLit(Raster(MultibandTile(tile), extent))
      }
    }
  }
}
