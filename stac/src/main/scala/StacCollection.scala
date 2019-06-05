package geotrellis.server.stac

import geotrellis.vector.{io => _, _}
import io.circe._

case class StacCollection(
    stacVersion: String,
    id: String,
    title: Option[String],
    description: String,
    keywords: List[String],
    version: String,
    license: String,
    providers: List[StacProvider],
    extent: Json,
    properties: JsonObject,
    links: List[StacLink]
)

object StacCollection {
  implicit val encStacCollection: Encoder[StacCollection] = Encoder.forProduct11(
    "stac_version",
    "id",
    "title",
    "description",
    "keywords",
    "version",
    "license",
    "providers",
    "extent",
    "properties",
    "links"
  )(
    collection =>
      (
        collection.stacVersion,
        collection.id,
        collection.title,
        collection.description,
        collection.keywords,
        collection.version,
        collection.license,
        collection.providers,
        collection.extent,
        collection.properties,
        collection.links
      )
  )

  implicit val decStacCollection: Decoder[StacCollection] = Decoder.forProduct11(
    "stac_version",
    "id",
    "title",
    "description",
    "keywords",
    "version",
    "license",
    "providers",
    "extent",
    "properties",
    "links"
  )(StacCollection.apply _)
}
