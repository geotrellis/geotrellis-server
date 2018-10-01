package geotrellis.server.core.maml

import geotrellis.server.core.maml.persistence._
import geotrellis.server.core.maml.metadata._
import geotrellis.server.core.maml.reification._

import geotrellis.server.core.cog.CogUtils

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


case class CogNode(uri: URI, band: Int, celltype: Option[CellType])

object CogNode {
  implicit val cellTypeEncoder: Encoder[CellType] = Encoder.encodeString.contramap[CellType](CellType.toName)
  implicit val cellTypeDecoder: Decoder[CellType] = Decoder[String].emap { name => Right(CellType.fromName(name)) }

  implicit val uriEncoder: Encoder[URI] = Encoder.encodeString.contramap[URI](_.toString)
  implicit val uriDecoder: Decoder[URI] = Decoder[String].emap { str => Right(URI.create(str)) }

  implicit val cogNodeEncoder: Encoder[CogNode] = deriveEncoder[CogNode]
  implicit val cogNodeDecoder: Decoder[CogNode] = deriveDecoder[CogNode]

  implicit val cogNodeRasterExtents: HasRasterExtents[CogNode] = new HasRasterExtents[CogNode] {
    def rasterExtents(self: CogNode)(implicit contextShift: ContextShift[IO]): IO[NEL[RasterExtent]] =
      CogUtils.getTiff(self.uri.toString).map { tiff =>
        NEL(tiff.rasterExtent, tiff.overviews.map(_.rasterExtent))
      }
    def crs(self: CogNode)(implicit contextShift: ContextShift[IO]): IO[CRS] =
      CogUtils.getTiff(self.uri.toString).map { tiff =>
        tiff.crs
      }
  }

  implicit val cogNodeTmsReification: MamlTmsReification[CogNode] = new MamlTmsReification[CogNode] {
    def kind(self: CogNode): MamlKind = MamlKind.Tile
    def tmsReification(self: CogNode, buffer: Int)(implicit contextShift: ContextShift[IO]): (Int, Int, Int) => IO[Literal] = (z: Int, x: Int, y: Int) => {
      def fetch(xCoord: Int, yCoord: Int) =
        CogUtils.fetch(self.uri.toString, z, xCoord, yCoord)
        .map(_.tile)
        .map(_.band(self.band))
      (fetch(x - 1, y + 1), fetch(x, y + 1), fetch(x + 1, y + 1),
       fetch(x - 1, y),     fetch(x, y),     fetch(x + 1, y),
       fetch(x - 1, y - 1), fetch(x, y - 1), fetch(x + 1, y - 1)).mapN { (tl, tm, tr, ml, mm, mr, bl, bm, br) =>
        val tile = TileWithNeighbors(mm, Some(NeighboringTiles(tl, tm, tr, ml, mr,bl, bm, br))).withBuffer(buffer)
        val extent = CogUtils.tmsLevels(z).mapTransform.keyToExtent(x, y)
        RasterLit(Raster(tile, extent))
      }
    }
  }

  implicit val cogNodeExtentReification: MamlExtentReification[CogNode] = new MamlExtentReification[CogNode] {
    def kind(self: CogNode): MamlKind = MamlKind.Tile
    def extentReification(self: CogNode)(implicit contextShift: ContextShift[IO]): (Extent, CellSize) => IO[Literal] = (extent: Extent, cs: CellSize) => {
      CogUtils.getTiff(self.uri.toString)
        .map { CogUtils.cropGeoTiffToTile(_, extent, cs, self.band) }
        .map { RasterLit(_) }
    }
  }
}

