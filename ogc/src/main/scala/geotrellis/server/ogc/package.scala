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

package geotrellis.server

import geotrellis.proj4.CRS
import geotrellis.raster.{CellSize, Histogram, MultibandTile, Raster}
import geotrellis.server.ogc.style.OgcStyle
import geotrellis.vector.Extent
import cats.Order
import org.threeten.extra.PeriodDuration
import jp.ne.opt.chronoscala.Imports._

import java.time.ZonedDateTime

package object ogc {
  implicit val ZonedDateTimeOrder: Order[ZonedDateTime] = Order.fromOrdering[ZonedDateTime]

  implicit class ExtentOps(val self: Extent) extends AnyVal {
    def swapXY: Extent                             = Extent(xmin = self.ymin, ymin = self.xmin, xmax = self.ymax, ymax = self.xmax)
    def buffer(cellSize: CellSize): Extent         = self.buffer(cellSize.width / 2, cellSize.height / 2)
    def buffer(cellSize: Option[CellSize]): Extent = cellSize.fold(self)(buffer)
  }

  implicit class RasterOps(val self: Raster[MultibandTile]) extends AnyVal {
    def render(crs: CRS, maybeStyle: Option[OgcStyle], format: OutputFormat, hists: List[Histogram[Double]]): Array[Byte] =
      if (self.tile.bandCount == 1) Render.singleband(self, crs, maybeStyle, format, hists)
      else Render.multiband(self, crs, maybeStyle, format, hists)
  }

  implicit class PeriodDurationOps(val self: PeriodDuration) extends AnyVal {
    def toMillis: Long = {
      val p = self.getPeriod
      p.getYears.toLong * 365 * 24 * 3600 * 1000 +
        p.getMonths.toLong * 30 * 24 * 3600 * 1000 +
        p.getDays * 24 * 3600 * 100 + self.getDuration.toMillis
    }
  }
}
