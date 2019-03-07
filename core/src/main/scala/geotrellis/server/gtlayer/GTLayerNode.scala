package geotrellis.server.gtlayer

import geotrellis.server._

import cats.effect._
import cats.data.{NonEmptyList => NEL}
import cats.syntax.all._
import cats.implicits._
import com.azavea.maml.eval.tile._
import io.circe._
import io.circe.generic.semiauto._
import geotrellis.proj4._
import geotrellis.raster._
import geotrellis.spark._
import geotrellis.spark.io._
import geotrellis.spark.stitch._
import geotrellis.vector._

import java.net.URI
import scala.util.Try

case class GTLayerNode(catalog: URI, layer: String) {
  lazy val collectionReader = CollectionLayerReader(catalog)
  lazy val (maxZoom, allMetadata): (Int, Map[Int, Option[TileLayerMetadata[SpatialKey]]]) = {
    val attributeStore = AttributeStore(catalog)
    val zs = attributeStore.layerIds
      .groupBy(_.name)
      .apply(layer)
      .map(_.zoom)
      .sortWith(_ > _)
    (zs.head, zs.map{ z => (z, Try(attributeStore.readMetadata[TileLayerMetadata[SpatialKey]](LayerId(layer, z))).toOption) }.toMap)
  }
  lazy val crs = allMetadata(maxZoom).get.crs
}

object GTLayerNode {
  type MetadataCatalog = Map[String, (Seq[Int], Option[TileLayerMetadata[SpatialKey]])]

  implicit val uriEncoder: Encoder[URI] = Encoder.encodeString.contramap[URI](_.toString)
  implicit val uriDecoder: Decoder[URI] = Decoder[String].emap { str => Right(URI.create(str)) }

  implicit val gtLayerNodeRasterExtents: HasRasterExtents[GTLayerNode] =
    new HasRasterExtents[GTLayerNode] {
      def rasterExtents(self: GTLayerNode)(implicit contextShift: ContextShift[IO]): IO[NEL[RasterExtent]] =
        IO {
          val allREs = Range(self.maxZoom, -1, -1).flatMap{ i =>
            val md = self.allMetadata(i)
            md.map{ meta =>
              val ex = meta.layout.mapTransform.boundsToExtent(meta.bounds.asInstanceOf[KeyBounds[SpatialKey]].toGridBounds)
              val cs = meta.cellSize
              RasterExtent(ex, cs)
            }
          }
          NEL(allREs.head, allREs.tail.toList)
        }

        def crs(self: GTLayerNode)(implicit contextShift: ContextShift[IO]): IO[CRS] =
          IO { self.crs }
    }

  implicit val gtLayerNodeTmsReification: TmsReification[GTLayerNode] =
    new TmsReification[GTLayerNode] {
      def tmsReification(self: GTLayerNode, buffer: Int)(implicit contextShift: ContextShift[IO]): (Int, Int, Int) => IO[ProjectedRaster[MultibandTile]] =
        (z: Int, x: Int, y: Int) => IO {
          val bounds = GridBounds(x - 1, y - 1, x + 1, y + 1)
          val values =
            self.collectionReader
              .query[SpatialKey, Tile, TileLayerMetadata[SpatialKey]](LayerId(self.layer, z))
              .where(Intersects(bounds))
              .result
              .toMap
          def p2t(p: (Int, Int)): Tile = values.get(SpatialKey(p._1+x, p._2+y)).get

          val nbhd =
            (p2t((-1, -1)), p2t((0, -1)), p2t((1, -1)), p2t((-1, 0)), p2t((1, 0)), p2t((-1, 1)), p2t((0, 1)), p2t((1, 1)))
          val neighboring = NeighboringTiles(nbhd._1, nbhd._2, nbhd._3, nbhd._4, nbhd._5, nbhd._6, nbhd._7, nbhd._8)
          val tile = TileWithNeighbors(values.get(SpatialKey(x, y)).get, Some(neighboring)).withBuffer(buffer)
          val ex = self.allMetadata(z).get.layout.mapTransform(SpatialKey(x, y))

          ProjectedRaster(MultibandTile(tile), ex, self.crs)
        }
    }

  implicit val gtLayerNodeExtentReification: ExtentReification[GTLayerNode] = new ExtentReification[GTLayerNode] {
    def extentReification(self: GTLayerNode)(implicit contextShift: ContextShift[IO]): (Extent, CellSize) => IO[ProjectedRaster[MultibandTile]] =
      { (ex: Extent, cs: CellSize) =>
        IO {
          def csToDiag(cell: CellSize) = math.sqrt(cell.width * cell.width + cell.height * cell.height)
          val reqDiag = csToDiag(cs)
          val z = self.allMetadata.mapValues{ md => csToDiag(md.get.cellSize) - reqDiag }
                                  .filter(_._2 <= 0)
                                  .map(_._1)
                                  .toList
                                  .sortBy{i => -i}
                                  .headOption
                                  .getOrElse(self.maxZoom)
          val raster = self.collectionReader
            .query[SpatialKey, Tile, TileLayerMetadata[SpatialKey]](LayerId(self.layer, z))
            .where(Intersects(ex))
            .result
            .stitch
            .crop(ex)
          ProjectedRaster(MultibandTile(raster.tile), raster.extent, self.crs)
        }
      }
  }

}
