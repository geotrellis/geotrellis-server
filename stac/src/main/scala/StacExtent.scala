package geotrellis.server.stac

import io.circe._
import io.circe.shapes.CoproductInstances
import io.circe.generic.semiauto._
import shapeless._

import java.time.Instant

case class StacExtent(
  spatial: TwoDimBbox :+: ThreeDimBbox :+: CNil,
  temporal: (Option[Instant], Option[Instant])
)

object StacExtent extends CoproductInstances {
  implicit val encStacExtent: Encoder[StacExtent] = deriveEncoder

  implicit val decStacExtent: Decoder[StacExtent] = deriveDecoder
}
