package geotrellis.server.ogc

import geotrellis.server.ogc.style._

import geotrellis.raster.render.{ColorRamp, RGBA}
import geotrellis.raster.histogram.DoubleHistogram

import org.scalatest.FunSpec
import org.scalatest._
import org.scalatest.Matchers._


class InterpolatedColorMapSpec extends FunSpec {
  describe("InterpolatedColorMap") {
    val minColor = RGBA(255, 0, 0, 100)
    val medColor = RGBA(0, 255, 0, 100)
    val maxColor = RGBA(0, 0, 255, 100)
    val clippedColor = RGBA(0, 0, 0, 0)
    val poles =
      Map[Double, Int](
        -100.0 -> minColor,
        0.0 -> medColor,
        100.0 -> maxColor
      )

    it("should interpolate based based on the two nearest poles") {
      val clipDefinition = ClipNone
      val interpolate =
        InterpolatedColorMap.interpolation(poles, clipDefinition)
      val interpolated = interpolate(50.0)
      val expected = RGBA(0, 127, 127, 100)

      interpolated shouldBe (expected)
      interpolated.red shouldBe (expected.red)
      interpolated.blue shouldBe (expected.blue)
      interpolated.green shouldBe (expected.green)
    }

    it("should respect clip definition: none") {
      val clipDefinition = ClipNone
      val interpolate =
        InterpolatedColorMap.interpolation(poles, clipDefinition)
      val i = interpolate(Double.MinValue)
      interpolate(Double.MinValue) shouldBe (minColor)
      interpolate(Double.MaxValue) shouldBe (maxColor)
    }

    it("should respect clip definition: left") {
      val clipDefinition = ClipLeft
      val interpolate =
        InterpolatedColorMap.interpolation(poles, clipDefinition)
      interpolate(Double.MinValue) shouldBe (clippedColor)
      interpolate(Double.MaxValue) shouldBe (maxColor)
    }

    it("should respect clip definition: right") {
      val clipDefinition = ClipRight
      val interpolate =
        InterpolatedColorMap.interpolation(poles, clipDefinition)
      interpolate(Double.MinValue) shouldBe (minColor)
      interpolate(Double.MaxValue) shouldBe (clippedColor)
    }

    it("should respect clip definition: both") {
      val clipDefinition = ClipBoth
      val interpolate =
        InterpolatedColorMap.interpolation(poles, clipDefinition)
      interpolate(Double.MinValue) shouldBe (clippedColor)
      interpolate(Double.MaxValue) shouldBe (clippedColor)
    }
  }
}