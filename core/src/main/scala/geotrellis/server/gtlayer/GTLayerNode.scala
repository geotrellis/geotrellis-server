/*
 * Copyright 2020 Azavea
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package geotrellis.server.gtlayer

import geotrellis.server._

import cats.effect._
import cats.data.{NonEmptyList => NEL}
import cats.implicits._
import com.azavea.maml.eval.tile._
import io.circe._

import geotrellis.store._
import geotrellis.layer._
import geotrellis.raster._
import geotrellis.vector._

import java.net.URI
import scala.util.Try

case class GTLayerNode(catalog: URI, layer: String) {
  lazy val collectionReader = CollectionLayerReader(catalog)

  lazy val (maxZoom, allMetadata): (Int, Map[Int, Option[TileLayerMetadata[SpatialKey]]]) = {
    val attributeStore = AttributeStore(catalog)
    val zs             =
      attributeStore.layerIds
        .groupBy(_.name)
        .apply(layer)
        .map(_.zoom)
        .sortWith(_ > _)

    (
      zs.head,
      zs.map { z =>
        (
          z,
          Try(
            attributeStore
              .readMetadata[TileLayerMetadata[SpatialKey]](LayerId(layer, z))
          ).toOption
        )
      }.toMap
    )
  }
  lazy val crs = allMetadata(maxZoom).get.crs
}

object GTLayerNode {
  type MetadataCatalog = Map[String, (Seq[Int], Option[TileLayerMetadata[SpatialKey]])]

  implicit val uriEncoder: Encoder[URI] = Encoder.encodeString.contramap[URI](_.toString)

  implicit val uriDecoder: Decoder[URI] = Decoder[String].emap { str => Right(URI.create(str)) }

  implicit def gtLayerNodeRasterExtents[F[_]: Sync]: HasRasterExtents[F, GTLayerNode] = { self =>
    Sync[F].delay {
      val allREs = Range(self.maxZoom, -1, -1).flatMap { i =>
        val md = self.allMetadata(i)
        md.map { meta =>
          val ex = meta.layout.mapTransform.boundsToExtent(
            meta.bounds.asInstanceOf[KeyBounds[SpatialKey]].toGridBounds
          )
          val cs = meta.cellSize
          RasterExtent(ex, cs)
        }
      }
      NEL(allREs.head, allREs.tail.toList)
    }
  }

  implicit def gtLayerNodeTmsReification[F[_]: Sync]: TmsReification[F, GTLayerNode] = { (self, buffer) => (z: Int, x: Int, y: Int) =>
    Sync[F].delay {
      val bounds = GridBounds(x - 1, y - 1, x + 1, y + 1)
      val values =
        self.collectionReader
          .query[SpatialKey, Tile, TileLayerMetadata[SpatialKey]](
            LayerId(self.layer, z)
          )
          .where(Intersects(bounds))
          .result
          .toMap

      def p2t(p: (Int, Int)): Tile =
        values.get(SpatialKey(p._1 + x, p._2 + y)).get

      val nbhd        = (
        p2t((-1, -1)),
        p2t((0, -1)),
        p2t((1, -1)),
        p2t((-1, 0)),
        p2t((1, 0)),
        p2t((-1, 1)),
        p2t((0, 1)),
        p2t((1, 1))
      )
      val neighboring = NeighboringTiles(
        nbhd._1,
        nbhd._2,
        nbhd._3,
        nbhd._4,
        nbhd._5,
        nbhd._6,
        nbhd._7,
        nbhd._8
      )
      val tile        = TileWithNeighbors(
        values.get(SpatialKey(x, y)).get,
        Some(neighboring)
      ).withBuffer(buffer)
      val ex          =
        self.allMetadata(z).get.layout.mapTransform(SpatialKey(x, y))

      ProjectedRaster(MultibandTile(tile), ex, self.crs)
    }
  }

  implicit def gtLayerNodeExtentReification[F[_]: Sync]: ExtentReification[F, GTLayerNode] = {
    self =>
      { (ex: Extent, cellSize: Option[CellSize]) =>
        Sync[F].delay {
          def csToDiag(cs: CellSize) =
            math.sqrt(cs.width * cs.width + cs.height * cs.height)

          val reqDiag = cellSize.map(csToDiag).getOrElse(0d)
          val z       = self.allMetadata
            .mapValues { md =>
              csToDiag(md.get.cellSize) - reqDiag
            }
            .filter(_._2 <= 0)
            .map(_._1)
            .toList
            .sortBy { i =>
              -i
            }
            .headOption
            .getOrElse(self.maxZoom)
          val raster  = self.collectionReader
            .query[SpatialKey, Tile, TileLayerMetadata[SpatialKey]](
              LayerId(self.layer, z)
            )
            .where(Intersects(ex))
            .result
            .stitch
            .crop(ex)
          ProjectedRaster(MultibandTile(raster.tile), raster.extent, self.crs)
        }
      }
  }
}
