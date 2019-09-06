package geotrellis.server.stac

import cats.syntax.either._
import cats.implicits._
import io.circe._

case class StacLink(
    href: String,
    rel: StacLinkType,
    _type: Option[StacMediaType],
    title: Option[String],
    labelExtAssets: List[String]
)

object StacLink {
  implicit val encStacLink: Encoder[StacLink] = Encoder.forProduct5(
    "href",
    "rel",
    "type",
    "title",
    "label:assets"
  )(link => (link.href, link.rel, link._type, link.title, link.labelExtAssets))

  implicit val decStacLink: Decoder[StacLink] = new Decoder[StacLink] {
    final def apply(c: HCursor) =
      (
        c.downField("href").as[String],
        c.downField("rel").as[StacLinkType],
        c.get[Option[StacMediaType]]("type"),
        c.get[Option[String]]("title"),
        c.get[List[String]]("label:assets")
      ).mapN(StacLink.apply _)
  }
}
