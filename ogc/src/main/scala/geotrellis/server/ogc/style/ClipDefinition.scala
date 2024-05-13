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

package geotrellis.server.ogc.style

import org.log4s._
import cats.syntax.option._

abstract class ClipDefinition(repr: String) extends Product with Serializable
case object ClipNone extends ClipDefinition("clip-none")
case object ClipLeft extends ClipDefinition("clip-left")
case object ClipRight extends ClipDefinition("clip-right")
case object ClipBoth extends ClipDefinition("clip-both")

object ClipDefinition {
  private val logger = getLogger

  def fromString(str: String): Option[ClipDefinition] =
    str match {
      case "clip-none"  => ClipNone.some
      case "clip-left"  => ClipLeft.some
      case "clip-right" => ClipRight.some
      case "clip-both"  => ClipBoth.some
      case _ =>
        logger.warn(s"Unable to deserialize string as ClipDefinition $str")
        None
    }

}
