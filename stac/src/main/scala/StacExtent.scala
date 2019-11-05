package geotrellis.server.stac

import geotrellis.server.stac.Implicits._

import io.circe._
import io.circe.shapes.CoproductInstances
import io.circe.generic.semiauto._
import shapeless._

import java.time.Instant

case class StacExtent(
  spatial: Bbox,
  temporal: TemporalExtent
)

object StacExtent {
  implicit val encStacExtent: Encoder[StacExtent] = deriveEncoder

  implicit val decStacExtent: Decoder[StacExtent] = deriveDecoder
}
