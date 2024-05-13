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

import io.circe.{Decoder, Encoder}
import cats.syntax.either._
import geotrellis.raster.CellSize

import scala.util.Try

case class ProjTransform(upx: Double, xres: Double, xskew: Double, upy: Double, yskew: Double, yres: Double) {
  def toArray: Array[Double] = Array(upx, xres, xskew, upy, yskew, yres)
  def toList: List[Double] = toArray.toList
  def cellSize: CellSize = CellSize(math.abs(xres), math.abs(yres))
}

object ProjTransform {
  def fromArray(transform: Array[Double]): ProjTransform = {
    val Array(upx, xres, xskew, upy, yskew, yres) = transform
    ProjTransform(upx, xres, xskew, upy, yskew, yres)
  }

  def fromList(transform: List[Double]): ProjTransform = fromArray(transform.toArray)

  def apply(transform: Array[Double]): Either[String, ProjTransform] =
    Try(fromArray(transform)).toEither.leftMap(_.getMessage)

  def apply(transform: List[Double]): Either[String, ProjTransform] = apply(transform.toArray)

  implicit val enProjGDALTransform: Encoder[ProjTransform] = Encoder.encodeList[Double].contramap(_.toList)
  implicit val decProjGDALTransform: Decoder[ProjTransform] = Decoder.decodeList[Double].emap { list =>
    val transform = if (list.length > 6) {
      // handle rasterio affine transform
      val List(a, b, c, d, e, f) = list.take(6)
      List(c, a, b, f, d, e)
    } else list
    apply(transform)
  }
}
