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

package geotrellis.stac.extensions.proj

import geotrellis.raster.Dimensions
import io.circe.{Decoder, Encoder}
import cats.syntax.either._

import scala.util.Try

case class ProjShape(cols: Long, rows: Long) {
  def toDimensions: Dimensions[Long] = Dimensions(cols, rows)
  def toList: List[Long]             = List(cols, rows)
}

object ProjShape {
  def apply(list: List[Long]): Either[String, ProjShape] =
    Try {
      val List(cols, rows) = list.take(2)
      ProjShape(cols, rows)
    }.toEither.leftMap(_.getMessage)

  implicit val enProjShape: Encoder[ProjShape]  = Encoder.encodeList[Long].contramap(_.toList)
  implicit val decProjShape: Decoder[ProjShape] = Decoder.decodeList[Long].emap(ProjShape.apply)
}
