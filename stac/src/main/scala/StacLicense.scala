package geotrellis.server.stac

import cats.implicits._
import io.circe._

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
  implicit val encoderStacLicense: Encoder[StacLicense] =
    Encoder.encodeString.contramap[StacLicense](_.name)

  implicit val decodeSpdx: Decoder[SPDX] = Decoder.decodeString.emap { s =>
    SpdxId.from(s) match {
      case Left(error) => Either.left(error)
      case Right(spdx) => Either.right(SPDX(spdx))
    }
  }

  implicit val decodeProprietary: Decoder[Proprietary] =
    Decoder.decodeString.emap {
      case "proprietary" => Either.right(Proprietary())
      case s             => Either.left(s"Unknown License: $s")
    }

  implicit val decodeStacLicense: Decoder[StacLicense] =
    List[Decoder[StacLicense]](
      Decoder[Proprietary].widen,
      Decoder[SPDX].widen
    ).reduceLeft(_ or _)

}
