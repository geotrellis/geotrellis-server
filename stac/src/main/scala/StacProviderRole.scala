package geotrellis.server.stac

import cats.implicits._
import io.circe._

sealed abstract class StacProviderRole(val repr: String) {
  override def toString: String = repr
}

object StacProviderRole {
  def fromString(s: String): StacProviderRole = s.toLowerCase match {
    case "licensor" => Licensor
    case "producer" => Producer
    case "processor" => Processor
    case "host" => Host
  }

  implicit val encProviderRole: Encoder[StacProviderRole] =
    Encoder.encodeString.contramap[StacProviderRole](_.toString)
  implicit val decProviderRole: Decoder[StacProviderRole] =
    Decoder.decodeString.emap { str =>
      Either.catchNonFatal(fromString(str)).leftMap(_ => "StacProviderRole")
    }
}

case object Licensor extends StacProviderRole("licensor")
case object Producer extends StacProviderRole("producer")
case object Processor extends StacProviderRole("processor")
case object Host extends StacProviderRole("host")
