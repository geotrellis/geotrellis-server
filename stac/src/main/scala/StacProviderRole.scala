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
