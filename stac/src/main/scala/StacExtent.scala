package geotrellis.server.stac

import geotrellis.server.stac.Implicits._

import io.circe._
import io.circe.shapes.CoproductInstances
import io.circe.generic.semiauto._
import io.circe.syntax._
import shapeless._

import java.time.Instant

case class SpatialExtent(bbox: List[Bbox])
object SpatialExtent {
  implicit val encSpatialExtent: Encoder[SpatialExtent] = deriveEncoder
  implicit val decSpatialExtent: Decoder[SpatialExtent] = deriveDecoder
}

case class Interval(interval: TemporalExtent)
object Interval {
  implicit val encInterval: Encoder[Interval] = deriveEncoder
  implicit val decInterval: Decoder[Interval] = deriveDecoder
}

case class StacExtent(
    spatial: SpatialExtent,
    temporal: Interval
)

object StacExtent {
  implicit val encStacExtent: Encoder[StacExtent] = deriveEncoder
  implicit val decStacExtent: Decoder[StacExtent] = deriveDecoder
}
