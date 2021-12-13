/*
 * Copyright 2021 Azavea
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

package geotrellis.server.ogc

import io.circe.Codec
import io.circe.generic.extras.Configuration
import io.circe.generic.extras.semiauto.deriveEnumerationCodec

/** ADT to change [[OgcTime]] internal representation */
sealed trait OgcTimeFormat

object OgcTimeFormat {

  /** Represent [[OgcTime]] as [[OgcTimePositions]]. */
  case object Positions extends OgcTimeFormat

  /** Represent [[OgcTime]] as [[OgcTimeInterval]]. */
  case object Interval extends OgcTimeFormat

  /** Don't change the internal [[OgcTime]] representation. */
  case object Default extends OgcTimeFormat

  implicit private val config: Configuration            = Configuration.default.copy(transformConstructorNames = _.toLowerCase)
  implicit val ogcTimeFormatCodec: Codec[OgcTimeFormat] = deriveEnumerationCodec
}
