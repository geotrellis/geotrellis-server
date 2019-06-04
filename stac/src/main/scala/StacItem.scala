package geotrellis.server.stac

import geotrellis.vector.{io => _, _}
import io.circe._
import shapeless._

case class StacItem(
    id: String,
    _type: String = "Feature",
    geometry: Geometry,
    bbox: Double :: Double :: Double :: Double :: HNil,
    links: List[StacLink],
    assets: Map[String, StacAsset],
    collection: Option[String],
    properties: JsonObject
)

object StacItem {
  implicit val encStacItem: Encoder[StacItem] = Encoder.forProduct8(
    "id",
    "type",
    "geometry",
    "bbox",
    "link",
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
    "id", "type", "geometry", "bbox", "link", "assets", "collection", "properties"
  )(StacItem.apply _)
}
