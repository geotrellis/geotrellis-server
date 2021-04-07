package geotrellis

import geotrellis.proj4.CRS
import geotrellis.vector._
import org.scalatest.Assertions
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

class ReprojectionSpec extends AnyFunSpec with Assertions with Matchers {
  describe("reprojection bug") {
    it("lcc <-> utm") {
      // target
      // Extent(-1710305.1057673902, 4366397.986532097, 4016158.3976479922, 1.0060133986247078E7)
      // reverse
      // Extent(-3130162.34420121, -1247871.8660286088, 2642422.623820399, 4654175.264342441)

      val sourceCRS = CRS.fromString("+proj=lcc +lat_1=49 +lat_2=77 +lat_0=49 +lon_0=-95 +x_0=0 +y_0=0 +ellps=GRS80 +towgs84=0,0,0,0,0,0,0 +units=m +no_defs")
      val targetCRS = CRS.fromString("+proj=utm +zone=13 +ellps=GRS80 +towgs84=0,0,0,0,0,0,0 +units=m +no_defs")

      val sourceExtent = Extent(-2328478.0, -732244.0, 2641142.0, 3898488.0)
      val targetExtent = sourceExtent.reproject(sourceCRS, targetCRS)
      val inverseExtent = targetExtent.reproject(targetCRS, sourceCRS)

      val sourceMin = Point(-2328478.0, -732244.0)
      val sourceMax = Point(2641142.0, 3898488.0)

      val targetMin = sourceMin.reproject(sourceCRS, targetCRS)
      val targetMax = sourceMax.reproject(sourceCRS, targetCRS)

      val inverseMin = targetMin.reproject(targetCRS, sourceCRS)
      val inverseMax = targetMax.reproject(targetCRS, sourceCRS)

      println(s"sourceMin: ${sourceMin}")
      println(s"sourceMax: ${sourceMax}")

      println(s"targetMin: ${targetMin}")
      println(s"targetMax: ${targetMax}")

      println(s"inverseMin: ${inverseMin}")
      println(s"inverseMax: ${inverseMax}")



      // println(s"sourceExtent: ${sourceExtent}")
      // println(s"targetExtent: ${targetExtent}")
      // println(targetExtent.toPolygon().exterior.points.toList)
      // println(s"inverseExtent: ${inverseExtent}")

      true shouldBe true
    }
  }
}
