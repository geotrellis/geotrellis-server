package geotrellis.server.ogc

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