package geotrellis.server.stac

import cats.syntax.either._
import cats.implicits._
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

  implicit val decStacLink: Decoder[StacLink] = new Decoder[StacLink] {
    final def apply(c: HCursor) =
      (
        c.downField("href").as[String],
        c.downField("rel").as[StacLinkType],
        c.get[Option[StacMediaType]]("type"),
        c.get[Option[String]]("title")
      ).mapN(StacLink.apply _)
  }
}
