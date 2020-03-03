/*
 * Copyright 2020 Azavea
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
