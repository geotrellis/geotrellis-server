package geotrellis.server.stac

import io.circe._
import io.circe.generic.semiauto._

case class StacProvider(
  name: String,
  description: Option[String],
  roles: List[StacProviderRole],
  url: Option[String]
)

object StacProvider {
  implicit val encStacProvider: Encoder[StacProvider] = deriveEncoder
  implicit val decStacProvider: Decoder[StacProvider] = deriveDecoder
}
