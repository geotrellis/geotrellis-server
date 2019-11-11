package geotrellis.server.stac

import cats.implicits._
import geotrellis.vector.{io => _}
import io.circe._
import io.circe.syntax._
import io.circe.refined._


final case class StacCollection(
    stacVersion: String,
    id: String,
    title: Option[String],
    description: String,
    keywords: List[String],
    version: String,
    license: StacLicense,
    providers: List[StacProvider],
    extent: StacExtent,
    properties: JsonObject,
    links: List[StacLink]
)

object StacCollection {

  implicit val encoderStacCollection: Encoder[StacCollection] =
    Encoder.forProduct11(
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

  implicit val decoderStacCollection: Decoder[StacCollection] =
    Decoder.forProduct11(
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
      (
          stacVersion: String,
          id: String,
          title: Option[String],
          description: String,
          keywords: Option[List[String]],
          version: Option[String],
          license: StacLicense,
          providers: Option[List[StacProvider]],
          extent: StacExtent,
          properties: Option[JsonObject],
          links: List[StacLink]
      ) =>
        StacCollection(
          stacVersion,
          id,
          title,
          description,
          keywords getOrElse List.empty,
          version getOrElse "0.0.0-alpha",
          license,
          providers getOrElse List.empty,
          extent,
          properties getOrElse JsonObject.fromMap(Map.empty),
          links
        )
    )
}
