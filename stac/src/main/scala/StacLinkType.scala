package geotrellis.server.stac

import cats.implicits._
import io.circe._

sealed abstract class StacLinkType(val repr: String) {
  override def toString = repr
}
case object Self extends StacLinkType("self")
case object Root extends StacLinkType("root")
case object Parent extends StacLinkType("parent")
case object Child extends StacLinkType("child")
case object Item extends StacLinkType("item")

object StacLinkType {

  private def fromString(s: String): StacLinkType = s.toLowerCase match {
    case "self" => Self
    case "root" => Root
    case "parent" => Parent
    case "child" => Child
    case "item" => Item
    case _ => throw new Exception("Cannot create StacLinkType of type $s")
  }

  implicit val encStacLinkType: Encoder[StacLinkType] =
    Encoder.encodeString.contramap[StacLinkType](_.toString)
  implicit val decStacLinkType: Decoder[StacLinkType] =
    Decoder.decodeString.emap { str =>
      Either.catchNonFatal(fromString(str)).leftMap(_ => "StacLinkType")
    }
}
