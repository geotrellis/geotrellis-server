package geotrellis.server.stac

import io.circe._

case class StacCatalog(
    stacVersion: String,
    id: String,
    title: Option[String],
    description: String,
    links: List[StacLink]
)

object StacCatalog {
  implicit val encCatalog: Encoder[StacCatalog] =
    Encoder.forProduct5("stac_version", "id", "title", "description", "links")(
      catalog =>
        (
          catalog.stacVersion,
          catalog.id,
          catalog.title,
          catalog.description,
          catalog.links
        )
    )

  implicit val decCatalog: Decoder[StacCatalog] =
    Decoder.forProduct5("stac_version", "id", "title", "description", "links")(
      StacCatalog.apply _)
}
