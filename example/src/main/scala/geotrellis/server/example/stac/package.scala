package geotrellis.server.example

import geotrellis.server.stac._
import geotrellis.server.vlm.RasterSourceUtils

import cats.data.{NonEmptyList => NEL}
import cats.effect.{IO, ContextShift}
import cats.implicits._
import geotrellis.contrib.vlm.RasterSource
import geotrellis.contrib.vlm.gdal.GDALRasterSource
import geotrellis.raster.{
  CellSize,
  GridExtent,
  IntArrayTile,
  MultibandTile,
  ProjectedRaster,
  RasterExtent,
  Tile
}
import geotrellis.proj4.{WebMercator, LatLng}
import geotrellis.raster.reproject.ReprojectRasterExtent
import geotrellis.raster.resample.NearestNeighbor
import geotrellis.server.{ExtentReification, HasRasterExtents, TmsReification}
import geotrellis.spark.SpatialKey
import geotrellis.vector.{io => _, _}

import com.typesafe.scalalogging.LazyLogging

package object stac extends RasterSourceUtils with LazyLogging {

  def getRasterSource(uri: String): RasterSource = new GDALRasterSource(uri)

  private val invisiTile = IntArrayTile.fill(0, 256, 256).withNoData(Some(0))

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
            getRasterSource(self.uri)
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
          getRasterSource(self.uri)
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
