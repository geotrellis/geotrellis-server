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
  implicit val encStacExtent: Encoder[StacExtent] = Encoder.forProduct2(
    "spatial",
    "temporal"
  )(
    extent =>
      (
        JsonObject.fromMap(Map("bbox" -> extent.spatial.asJson)),
        JsonObject.fromMap(Map("interval" -> extent.temporal.asJson))
      )
  )

  // TODO: this should go into the spatial field to pluck the bbox key
  // and the temporal field to pluck the interval key
  implicit val decStacExtent: Decoder[StacExtent] = deriveDecoder
}
