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

package geotrellis.server.ogc

import geotrellis.server.ogc.style._

import geotrellis.raster.render.ColorRamp
import geotrellis.raster.histogram.DoubleHistogram

import org.scalatest.FunSpec
import org.scalatest._
import org.scalatest.Matchers._

class OgcStyleSpec extends FunSpec {

  describe("ColorRampStyle") {
    it("should interpolate breaks") {
      val minimum = -10
      val maximum = 90
      val desiredBreaks = 50
      val ramp = ColorRamp(Array(0xff0000, 0x0000ff))
      val style = ColorRampStyle("test", "title", ramp, Some(20), Some(minimum), Some(maximum))
      val breaks = style.breaks(List(DoubleHistogram()), desiredBreaks)
      breaks.length shouldBe (desiredBreaks)
      breaks.head shouldBe (-10)
      breaks.reverse.head shouldBe (90)
    }
  }
}