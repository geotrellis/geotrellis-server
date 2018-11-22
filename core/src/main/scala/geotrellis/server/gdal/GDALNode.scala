package geotrellis.server.gdal

import geotrellis.server._
import geotrellis.server.cog.util.CogUtils
import com.azavea.maml.ast.{Expression, Literal, MamlKind, RasterLit}
import com.azavea.maml.eval.tile._
import io.circe._
import io.circe.generic.semiauto._
import cats.effect._
import cats.data.{NonEmptyList => NEL}
import cats.syntax.all._
import geotrellis.raster._
import geotrellis.proj4.CRS
import geotrellis.vector.Extent
import java.net.URI

import geotrellis.server.gdal.vlm.GDALUtils

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
      CogUtils.getTiff(self.uri.toString).map { tiff =>
        NEL(tiff.rasterExtent, tiff.overviews.map(_.rasterExtent))
      }
    def crs(self: GDALNode)(implicit contextShift: ContextShift[IO]): IO[CRS] =
      CogUtils.getTiff(self.uri.toString).map { tiff =>
        tiff.crs
      }
  }

  implicit val GDALNodeTmsReification: TmsReification[GDALNode] = new TmsReification[GDALNode] {
    def kind(self: GDALNode): MamlKind = MamlKind.Image
    def tmsReification(self: GDALNode, buffer: Int)(implicit contextShift: ContextShift[IO]): (Int, Int, Int) => IO[Literal] = (z: Int, x: Int, y: Int) => {
      def fetch(xCoord: Int, yCoord: Int) =
        GDALUtils.fetch(self.uri.toString, z, xCoord, yCoord)
          .map(_.tile)
          .map(_.band(self.band))

      fetch(x, y).map { tile =>
        val extent = CogUtils.tmsLevels(z).mapTransform.keyToExtent(x, y)
        RasterLit(Raster(MultibandTile(tile), extent))
      }

      /*(fetch(x - 1, y + 1), fetch(x, y + 1), fetch(x + 1, y + 1),
        fetch(x - 1, y),     fetch(x, y),     fetch(x + 1, y),
        fetch(x - 1, y - 1), fetch(x, y - 1), fetch(x + 1, y - 1)).parMapN { (tl, tm, tr, ml, mm, mr, bl, bm, br) =>
        val tile = TileWithNeighbors(mm, Some(NeighboringTiles(tl, tm, tr, ml, mr,bl, bm, br))).withBuffer(buffer)
        val extent = CogUtils.tmsLevels(z).mapTransform.keyToExtent(x, y)
        RasterLit(Raster(MultibandTile(tile), extent))
      }*/
    }
  }

  /*implicit val GDALNodeExtentReification: ExtentReification[GDALNode] = new ExtentReification[GDALNode] {
    def kind(self: GDALNode): MamlKind = MamlKind.Image
    def extentReification(self: GDALNode)(implicit contextShift: ContextShift[IO]): (Extent, CellSize) => IO[Literal] = (extent: Extent, cs: CellSize) => {
      CogUtils.getTiff(self.uri.toString)
        .map { CogUtils.cropGeoTiffToTile(_, extent, cs, self.band) }
        .map { RasterLit(_) }
    }
  }*/
}
