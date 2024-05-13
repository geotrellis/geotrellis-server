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

import geotrellis.raster.render.RGBA

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

class InterpolatedColorMapSpec extends AnyFunSpec with Matchers {
  describe("InterpolatedColorMap") {
    val minColor = RGBA.fromRGBA(255, 0, 0, 100).int
    val medColor = RGBA.fromRGBA(0, 255, 0, 100).int
    val maxColor = RGBA.fromRGBA(0, 0, 255, 100).int
    val clippedColor = RGBA.fromRGBA(0, 0, 0, 0).int
    val poles =
      Map[Double, Int](
        -100.0 -> minColor,
        0.0 -> medColor,
        100.0 -> maxColor
      )

    it("should interpolate based based on the two nearest poles") {
      val clipDefinition = ClipNone
      val interpolate = InterpolatedColorMap.interpolation(poles, clipDefinition)
      val interpolated = RGBA(interpolate(50.0))
      val expected = RGBA.fromRGBA(0, 127, 127, 100)

      interpolated shouldBe expected
      interpolated.red shouldBe expected.red
      interpolated.blue shouldBe expected.blue
      interpolated.green shouldBe expected.green
    }

    it("should respect clip definition: none") {
      val clipDefinition = ClipNone
      val interpolate = InterpolatedColorMap.interpolation(poles, clipDefinition)
      val i = interpolate(Double.MinValue)
      interpolate(Double.MinValue) shouldBe minColor
      interpolate(Double.MaxValue) shouldBe maxColor
    }

    it("should respect clip definition: left") {
      val clipDefinition = ClipLeft
      val interpolate = InterpolatedColorMap.interpolation(poles, clipDefinition)
      interpolate(Double.MinValue) shouldBe clippedColor
      interpolate(Double.MaxValue) shouldBe maxColor
    }

    it("should respect clip definition: right") {
      val clipDefinition = ClipRight
      val interpolate = InterpolatedColorMap.interpolation(poles, clipDefinition)
      interpolate(Double.MinValue) shouldBe minColor
      interpolate(Double.MaxValue) shouldBe clippedColor
    }

    it("should respect clip definition: both") {
      val clipDefinition = ClipBoth
      val interpolate = InterpolatedColorMap.interpolation(poles, clipDefinition)
      interpolate(Double.MinValue) shouldBe clippedColor
      interpolate(Double.MaxValue) shouldBe clippedColor
    }
  }
}
