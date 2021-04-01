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

import cats.data.NonEmptyList
import io.circe.Decoder
import cats.syntax.either._

import java.time.ZonedDateTime
import scala.util.Try

sealed trait OgcTimeDefault {
  lazy val name: String = getClass.getName.split("\\$").last.toLowerCase
}

object OgcTimeDefault {
  case object Oldest                   extends OgcTimeDefault
  case object Newest                   extends OgcTimeDefault
  case class Time(time: ZonedDateTime) extends OgcTimeDefault

  def fromString(str: String): OgcTimeDefault = str match {
    case Oldest.name => Oldest
    case Newest.name => Newest
    case _           => Time(ZonedDateTime.parse(str))
  }

  implicit val ogcTimeDefaultDecoder: Decoder[OgcTimeDefault] = Decoder[String].emap { s =>
    Try(fromString(s)).toEither.leftMap(_.getMessage)
  }

  implicit class OgcTimeDefaultOps(val self: OgcTimeDefault) extends AnyVal {
    def selectTime(list: NonEmptyList[ZonedDateTime]): ZonedDateTime =
      self match {
        case OgcTimeDefault.Oldest  => list.head
        case OgcTimeDefault.Newest  => list.last
        case OgcTimeDefault.Time(t) => t
      }
  }
}
