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

import geotrellis.raster.CellSize
import geotrellis.vector.Extent

package object ogc {
  implicit class ExtentOps(self: Extent) {
    def swapXY: Extent                             = Extent(xmin = self.ymin, ymin = self.xmin, xmax = self.ymax, ymax = self.xmax)
    def buffer(cellSize: CellSize): Extent         = self.buffer(cellSize.width / 2, cellSize.height / 2)
    def buffer(cellSize: Option[CellSize]): Extent = cellSize.fold(self)(buffer)
  }
}
