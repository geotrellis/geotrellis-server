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

  val cogUri: Option[String] = assets
    .filter(_._2._type == Some(`image/cog`))
    .values
    .headOption map { _.href }
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
