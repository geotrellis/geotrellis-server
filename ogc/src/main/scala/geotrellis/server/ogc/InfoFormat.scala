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

import scala.util.Try

sealed trait InfoFormat {
  def name: String
  override def toString: String = name
}

object InfoFormat {
  case object XML extends InfoFormat { val name: String = "text/xml" }
  case object Json extends InfoFormat { val name: String = "application/json" }

  def fromStringUnsafe(str: String): InfoFormat =
    str match {
      case XML.name  => XML
      case Json.name => Json
    }

  def fromString(str: String): Option[InfoFormat] = Try(fromStringUnsafe(str)).toOption

  val all: List[String] = List(XML, Json).map(_.name)
}
