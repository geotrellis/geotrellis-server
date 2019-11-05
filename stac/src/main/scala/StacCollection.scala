package geotrellis.server.stac

import cats.implicits._
import geotrellis.vector.{io => _}
import io.circe._
import io.circe.syntax._
import io.circe.refined._

sealed trait StacCollection {
  val stacVersion: String
  val id: String
  val title: Option[String]
  val description: String
  val keywords: List[String]
  val version: String
  val license: StacLicense
  val providers: List[StacProvider]
  val extent: StacExtent
  val properties: JsonObject
  val links: List[StacLink]
}

case class PublicStacCollection(
    stacVersion: String,
    id: String,
    title: Option[String],
    description: String,
    keywords: List[String],
    version: String,
    license: SPDX,
    providers: List[StacProvider],
    extent: StacExtent,
    properties: JsonObject,
    links: List[StacLink]
) extends StacCollection

case class ProprietaryStacCollection(
    stacVersion: String,
    id: String,
    title: Option[String],
    description: String,
    keywords: List[String],
    version: String,
    license: Proprietary,
    providers: List[StacProvider],
    extent: StacExtent,
    properties: JsonObject,
    linksWithLicenseLink: StacLinksWithLicense
) extends StacCollection {
  val links: List[StacLink] = linksWithLicenseLink.value
}

object StacCollection {

  implicit val encoderPublicCollection: Encoder[PublicStacCollection] =
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

  implicit val encoderProprietaryCollection
      : Encoder[ProprietaryStacCollection] =
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

  implicit val encodeStacCollection: Encoder[StacCollection] =
    Encoder.instance {
      case public: PublicStacCollection =>
        public.asJson
      case proprietary: ProprietaryStacCollection =>
        proprietary.asJson
    }

  implicit val decoderPublicStacCollection: Decoder[PublicStacCollection] =
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
    )(PublicStacCollection.apply _)

  implicit val decoderProprietaryStacCollection
      : Decoder[ProprietaryStacCollection] =
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
    )(ProprietaryStacCollection.apply _)

  implicit val decoderStacCollection: Decoder[StacCollection] =
    Decoder[PublicStacCollection].widen or Decoder[ProprietaryStacCollection].widen
}
