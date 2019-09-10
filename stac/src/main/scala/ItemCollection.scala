package geotrellis.server.stac

import io.circe._

case class ItemCollection(
    _type: String = "FeatureCollection",
    features: List[StacItem],
    links: List[StacLink]
)

object ItemCollection {
  implicit val encItemCollection: Encoder[ItemCollection] = Encoder.forProduct3(
    "type",
    "features",
    "links"
  )(
    itemCollection =>
      (
        itemCollection._type,
        itemCollection.features,
        itemCollection.links
      )
  )

  implicit val decItemCollection: Decoder[ItemCollection] = Decoder.forProduct3(
    "type",
    "features",
    "links"
  )(ItemCollection.apply _)
}
