package geotrellis.server.stac

import geotrellis.server.TmsReification
import geotrellis.server.stac.Implicits._
import geotrellis.server.vlm.RasterSourceUtils

import cats.effect.{ContextShift, IO}
import com.azavea.maml.error.NonEvaluableNode
import com.azavea.maml.ast.RasterVar
import geotrellis.contrib.vlm.RasterSource
import geotrellis.contrib.vlm.geotiff.GeoTiffRasterSource
import geotrellis.proj4.{LatLng, WebMercator}
import geotrellis.raster.{IntArrayTile, MultibandTile, ProjectedRaster}
import geotrellis.vector.{io => _, _}
import io.circe._
import shapeless._
import com.azavea.maml.error.NonEvaluableNode

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
  println(assets)
  val uri = assets.filter(_._2._type == Some(`image/cog`)).values.headOption map { _.href } getOrElse {
    println("item does not have a cog asset")
    throw new IllegalArgumentException(s"Item $id does not have a cog asset")
  }
}

object StacItem extends RasterSourceUtils {

  def getRasterSource(uri: String): RasterSource = new GeoTiffRasterSource(uri)

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
        val invisiTile = IntArrayTile.fill(0, 256, 256).withNoData(Some(0))

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
            rasterSource.reproject(WebMercator).read(extent) map { rast =>
              ProjectedRaster(rast.tile, extent, WebMercator)
            } getOrElse {
              throw new Exception(
                s"Item ${self.id} claims to have data in ${extent} but did not"
              )
            }
          }
        }
      }
    }
}
