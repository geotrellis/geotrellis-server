package geotrellis.server.stac

import geotrellis.server.stac.Implicits._

import cats.implicits._
import geotrellis.contrib.vlm.RasterSource
import geotrellis.vector.{io => _, _}
import io.circe._

case class StacItem(
    id: String,
    _type: String = "Feature",
    geometry: Geometry,
    bbox: TwoDimBbox,
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

object StacItem {

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

}
