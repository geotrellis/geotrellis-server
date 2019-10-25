package geotrellis.server.stac

import cats.implicits._
import io.circe._

sealed abstract class StacLinkType(val repr: String) {
  override def toString = repr
}
case object Self extends StacLinkType("self")
case object StacRoot extends StacLinkType("root")
case object Parent extends StacLinkType("parent")
case object Child extends StacLinkType("child")
case object Item extends StacLinkType("item")
case object Items extends StacLinkType("items")
case object Source extends StacLinkType("source")
case object Collection extends StacLinkType("collection")
case class VendorLinkType(underlying: String) extends StacLinkType("vendor") {
  override def toString = s"$repr-$underlying"
}

object StacLinkType {

  private def fromString(s: String): StacLinkType = s.toLowerCase match {
    case "self"       => Self
    case "root"       => StacRoot
    case "parent"     => Parent
    case "child"      => Child
    case "item"       => Item
    case "items"      => Items
    case "source"     => Source
    case "collection" => Collection
    case s            => VendorLinkType(s)
  }

  implicit val encStacLinkType: Encoder[StacLinkType] =
    Encoder.encodeString.contramap[StacLinkType](_.toString)
  implicit val decStacLinkType: Decoder[StacLinkType] =
    Decoder.decodeString.emap { str =>
      Either.catchNonFatal(fromString(str)).leftMap(_ => "StacLinkType")
    }
}
