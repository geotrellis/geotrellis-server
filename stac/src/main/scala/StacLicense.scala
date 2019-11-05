package geotrellis.server.stac

import cats.implicits._
import eu.timepit.refined.api.RefType
import io.circe._
import io.circe.syntax._

sealed trait StacLicense {
  val name: String
}

final case class Proprietary() extends StacLicense {
  val name: String = "proprietary"
}

final case class SPDX(spdxId: SpdxId) extends StacLicense {
  val name = spdxId.value
}

object StacLicense {

  implicit val encoderSpdxLicense: Encoder[SPDX] =
    Encoder.encodeString.contramap(_.name)

  implicit val encoderProprietary: Encoder[Proprietary] =
    Encoder.encodeString.contramap(_.name)

  implicit val encoderStacLicense: Encoder[StacLicense] = Encoder.instance {
    case spdx: SPDX               => spdx.asJson
    case proprietary: Proprietary => proprietary.asJson
  }

  implicit val decodeSpdx: Decoder[SPDX] =
    Decoder.decodeString.emap {
      case s => RefType.applyRef[SpdxId](s) map (id => SPDX(id))
    }

  implicit val decodeProprietary: Decoder[Proprietary] =
    Decoder.decodeString.emap {
      case "proprietary" => Either.right(Proprietary())
      case s             => Either.left(s"Unknown Proprietary License: $s")
    }

  implicit val decodeStacLicense: Decoder[StacLicense] =
    Decoder[SPDX].widen or Decoder[Proprietary].widen

}
