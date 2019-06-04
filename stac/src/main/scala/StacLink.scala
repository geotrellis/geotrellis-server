package geotrellis.server.stac

import io.circe._

case class StacLink(
    href: String,
    rel: StacLinkType,
    _type: Option[StacMediaType],
    title: Option[String]
)

object StacLink {
  implicit val encStacLink: Encoder[StacLink] = Encoder.forProduct4(
    "href",
    "rel",
    "type",
    "title"
  )(link => (link.href, link.rel, link._type, link.title))

  implicit val decStacLink: Decoder[StacLink] = Decoder.forProduct4(
    "href", "rel", "type", "title"
  )(StacLink.apply _)
}
