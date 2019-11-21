package geotrellis.server.example

import geotrellis.server.stac._
import geotrellis.server.vlm.RasterSourceUtils

import cats.data.{NonEmptyList => NEL}
import cats.effect.{IO, ContextShift}
import cats.implicits._

import geotrellis.raster.gdal.GDALRasterSource
import geotrellis.raster._
import geotrellis.layer._
import geotrellis.proj4.{WebMercator, LatLng}
import geotrellis.raster.reproject.ReprojectRasterExtent
import geotrellis.raster.resample.NearestNeighbor
import geotrellis.server.{ExtentReification, HasRasterExtents, TmsReification}
import geotrellis.vector._

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
          self.cogUri traverse { cogUri =>
            IO {
              getRasterSource(cogUri)
            } map { rasterSource =>
              rasterSource
                .reproject(WebMercator)
                .tileToLayout(tmsLevels(z))
                .read(SpatialKey(x, y)) map { rast =>
                ProjectedRaster(rast mapBands { (_: Int, t: Tile) =>
                  t.toArrayTile
                }, extent, WebMercator)
              }
            } flatMap {
              case Some(t) => IO.pure(t)
              case None =>
                IO.raiseError(
                  new Exception(
                    s"STAC Item at $cogUri claims to have data in $extent, but it does not"
                  )
                )
            }
          } flatMap {
            case Some(t) => IO.pure(t)
            case None =>
              IO.raiseError(new Exception("STAC item did not have a uri"))
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
          self.cogUri traverse { cogUri =>
            IO {
              getRasterSource(cogUri)
            } map { rasterSource =>
              val intersects =
                extent.intersects(rasterSource.reproject(WebMercator).extent)
              if (intersects) {
                val rasterExtent = RasterExtent(extent, cellSize)
                rasterSource
                  .reproject(WebMercator, DefaultTarget)
                  .resampleToGrid(
                    GridExtent[Long](
                      rasterExtent.extent,
                      rasterExtent.cellSize
                    ),
                    NearestNeighbor
                  )
                  .read(extent, List(0, 1, 2))
                  .map(
                    rast => ProjectedRaster[MultibandTile](rast, WebMercator)
                  )
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
          } flatMap {
            case Some(t) => IO.pure(t)
            case None =>
              IO.raiseError(new Exception("STAC item did not have a uri"))
          }
        }
    }

  implicit val stacItemHasRasterExtents: HasRasterExtents[StacItem] =
    new HasRasterExtents[StacItem] {
      def rasterExtents(self: StacItem)(
          implicit contextShift: ContextShift[IO]
      ): IO[NEL[RasterExtent]] =
        self.cogUri traverse { cogUri =>
          IO {
            getRasterSource(cogUri)
          } map { rasterSource =>
            (rasterSource.resolutions map { cs =>
              val re = RasterExtent(rasterSource.extent, cs)
              ReprojectRasterExtent(
                RasterExtent(
                  re.extent,
                  re.cellwidth,
                  re.cellheight,
                  re.cols.toInt,
                  re.rows.toInt
                ),
                rasterSource.crs,
                WebMercator
              )
            }).toNel
          } flatMap {
            case Some(extents) => IO.pure(extents)
            case None =>
              IO.raiseError(
                new Exception(s"No extents available for ${self.id}")
              )
          }
        } flatMap {
          case Some(extents) => IO.pure(extents)
          case None =>
            IO.raiseError(new Exception("STAC item did not have a uri"))
        }
    }
}
