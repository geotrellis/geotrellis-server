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

import geotrellis.raster.render._
import geotrellis.raster.{RGBA => _, _}

import scala.math._
import java.util.Arrays.binarySearch

case class InterpolatedColorMap(
  poles: Map[Double, Int],
  clipDefinition: ClipDefinition
) {
  import InterpolatedColorMap._

  def interpolate: Double => Int = { dbl: Double => interpolation(poles, clipDefinition)(dbl) }

  def render(tile: Tile): Tile = {
    // IntCellType is used to store interpolated colors, since the source cellType can have not enough bits to encode interpolated colors.
    // See https://github.com/locationtech/geotrellis/blob/v3.6.0/raster/src/main/scala/geotrellis/raster/render/ColorMap.scala#L164-L176
    val result = ArrayTile.empty(IntCellType, tile.cols, tile.rows)
    if (tile.cellType.isFloatingPoint) {
      tile.foreachDouble { (col, row, z) =>
        if (isData(z)) result.setDouble(col, row, interpolate(z))
        else result.setDouble(col, row, 0d)
      }
    } else {
      tile.foreach { (col, row, z) =>
        if (isData(z)) result.setDouble(col, row, interpolate(z))
        else result.set(col, row, 0)
      }
    }
    result
  }
}

object InterpolatedColorMap {

  /** RGB color interpolation logic */
  private def RgbLerp(color1: RGBA, color2: RGBA, proportion: Double): RGBA = {
    val r         = (color1.red + (color2.red - color1.red) * proportion).toInt
    val g         = (color1.green + (color2.green - color1.green) * proportion).toInt
    val b         = (color1.blue + (color2.blue - color1.blue) * proportion).toInt
    val a: Double = (color1.alpha + (color2.alpha - color1.alpha) * proportion) / 2.55
    RGBA.fromRGBAPct(r, g, b, a)
  }

  /** For production of colors along a continuum */
  def interpolation(poles: Map[Double, Int], clipDefinition: ClipDefinition): Double => Int = { dbl: Double =>
    val decomposed            = poles.toArray.sortBy(_._1).unzip
    val breaks: Array[Double] = decomposed._1
    val colors: Array[Int]    = decomposed._2

    val insertionPoint: Int = binarySearch(breaks, dbl)
    if (insertionPoint == -1) {
      // MIN VALUE
      clipDefinition match {
        case ClipNone | ClipRight => colors(0)
        case ClipLeft | ClipBoth  => 0x00000000
      }
    } else if (abs(insertionPoint) - 1 == breaks.length) {
      // MAX VALUE
      clipDefinition match {
        case ClipNone | ClipLeft  => colors.last
        case ClipRight | ClipBoth => 0x00000000
      }
    } else if (insertionPoint < 0) {
      // MUST INTERPOLATE
      val lowerIdx   = abs(insertionPoint) - 2
      val higherIdx  = abs(insertionPoint) - 1
      val lower      = breaks(lowerIdx)
      val higher     = breaks(higherIdx)
      val proportion = (dbl - lower) / (higher - lower)

      RgbLerp(RGBA(colors(lowerIdx)), RGBA(colors(higherIdx)), proportion).int
    } else {
      // Direct hit
      colors(insertionPoint)
    }
  }
}
