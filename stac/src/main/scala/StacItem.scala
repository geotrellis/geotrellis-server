package geotrellis.server.stac

import geotrellis.server.{ExtentReification, HasRasterExtents, TmsReification}
import geotrellis.server.stac.Implicits._
import geotrellis.server.vlm.RasterSourceUtils

import cats.data.{NonEmptyList => NEL}
import cats.effect.{ContextShift, IO}
import cats.implicits._
import com.azavea.maml.error.NonEvaluableNode
import geotrellis.contrib.vlm.RasterSource
import geotrellis.contrib.vlm.gdal.GDALRasterSource
import geotrellis.proj4.{LatLng, WebMercator}
import geotrellis.raster.{
  CellSize,
  GridExtent,
  IntArrayTile,
  MultibandTile,
  ProjectedRaster,
  RasterExtent,
  Tile
}
import geotrellis.raster.reproject.ReprojectRasterExtent
import geotrellis.raster.resample.NearestNeighbor
import geotrellis.spark.SpatialKey
import geotrellis.vector.{io => _, _}
import io.circe._
import shapeless._
import com.azavea.maml.error.NonEvaluableNode

import com.typesafe.scalalogging.LazyLogging

case class StacItem(
    id: String,
    _type: String = "Feature",
    geometry: Geometry,
    bbox: Double :: Double :: Double :: Double :: HNil,
    links: List[StacLink],
    assets: Map[String, StacAsset],
    collection: Option[String],
    properties: JsonObject
) {
  val uri = assets
    .filter(_._2._type == Some(`image/cog`))
    .values
    .headOption map { _.href } getOrElse {
    throw new IllegalArgumentException(s"Item $id does not have a cog asset")
  }
}

object StacItem extends RasterSourceUtils with LazyLogging {

  def getRasterSource(uri: String): RasterSource = new GDALRasterSource(uri)
  private val invisiTile = IntArrayTile.fill(0, 256, 256).withNoData(Some(0))

  implicit val encStacItem: Encoder[StacItem] = Encoder.forProduct8(
    "id",
    "type",
    "geometry",
    "bbox",
    "links",
    "assets",
    "collection",
    "properties"
  )(
    item =>
      (
        item.id,
        item._type,
        item.geometry,
        item.bbox.toList,
        item.links,
        item.assets,
        item.collection,
        item.properties
      )
  )

  implicit val decStacItem: Decoder[StacItem] = Decoder.forProduct8(
    "id",
    "type",
    "geometry",
    "bbox",
    "links",
    "assets",
    "collection",
    "properties"
  )(StacItem.apply _)

  implicit val stacItemTmsReification: TmsReification[StacItem] =
    new TmsReification[StacItem] {
      def tmsReification(self: StacItem, buffer: Int)(
          implicit contextShift: ContextShift[IO]
      ) = (z: Int, x: Int, y: Int) => {
        val extent = tmsLevels(z).mapTransform.keyToExtent(x, y)
        val cs = tmsLevels(z).cellSize
        logger.debug(s"Extent to read: $extent")
        logger.debug(s"Cell size to read: $cs")

        if (!Projected(extent.toPolygon, 3857)
              .reproject(WebMercator, LatLng)(4326)
              .geom
              .intersects(self.geometry)) {
          IO.pure {
            ProjectedRaster[MultibandTile](
              MultibandTile(invisiTile, invisiTile, invisiTile),
              extent,
              WebMercator
            )
          }
        } else {
          IO {
            getRasterSource(self.uri)
          } map { rasterSource =>
            rasterSource
              .reproject(WebMercator)
              .tileToLayout(tmsLevels(z))
              .read(SpatialKey(x, y)) map { rast =>
              ProjectedRaster(rast mapBands { (_: Int, t: Tile) =>
                t.toArrayTile
              }, extent, WebMercator)
            } getOrElse {
              throw new Exception(
                s"Item ${self.id} claims to have data in ${extent} but did not"
              )
            }
          }
        }
      }
    }

  implicit val stacItemExtentReification: ExtentReification[StacItem] =
    new ExtentReification[StacItem] {
      def extentReification(
          self: StacItem
      )(implicit contextShift: ContextShift[IO]) =
        (extent: Extent, cellSize: CellSize) => {
          IO {
            StacItem.getRasterSource(self.uri)
          } map { rasterSource =>
            val intersects = extent.intersects(rasterSource.reproject(WebMercator).extent)
            if (intersects) {
              val rasterExtent = RasterExtent(extent, cellSize)
              rasterSource
                .reproject(WebMercator, NearestNeighbor)
                .resampleToGrid(
                  GridExtent[Long](rasterExtent.extent, rasterExtent.cellSize),
                  NearestNeighbor
                )
                .read(extent, List(0, 1, 2))
                .map(rast => ProjectedRaster[MultibandTile](rast, WebMercator))
                .getOrElse {
                  throw new Exception(
                    s"Item ${self.id} claims to have data in ${extent} but did not"
                  )
                }
            } else {
              println(s"Requested extent did not intersect geometry: $extent")
              ProjectedRaster[MultibandTile](
                MultibandTile(invisiTile, invisiTile, invisiTile),
                extent,
                WebMercator
              )
            }
          }
        }
    }

  implicit val stacItemHasRasterExtents: HasRasterExtents[StacItem] =
    new HasRasterExtents[StacItem] {
      def rasterExtents(self: StacItem)(
          implicit contextShift: ContextShift[IO]
      ): IO[NEL[RasterExtent]] =
        IO {
          StacItem.getRasterSource(self.uri)
        } map { rasterSource =>
          (rasterSource.resolutions map { res =>
            ReprojectRasterExtent(RasterExtent(res.extent,
              res.cellwidth,
              res.cellheight,
              res.cols.toInt,
              res.rows.toInt),
              rasterSource.crs,
              WebMercator)
          }).toNel match {
            case Some(extents) => extents
            case None => throw new Exception(s"No extents available for ${self.id}")
          }
        }
    }
}
