package geotrellis.server

import geotrellis.server.extent.SampleUtils

import geotrellis.raster._
import geotrellis.vector._
import cats.implicits._

import org.scalatest._

import scala.util.Random


class HistogramHeuristicsTest extends FunSuite with Matchers {
  test("extents sampled from within overall extent") {
    val random = new Random(1337)
    for (x <- 0 to 1000) {
      val scale = scala.math.abs(random.nextInt)
      val randomXMin = random.nextDouble
      val randomYMin = random.nextDouble

      // To avoid 0 width/heights
      val randomXMaxOffset = random.nextInt(1000).toDouble + 1
      val randomYMaxOffset = random.nextInt(1000).toDouble + 1

      val uberExtent = Extent(randomXMin, randomYMin, randomXMin + randomXMaxOffset, randomYMin + randomYMaxOffset)
      val cs = CellSize(10 * random.nextDouble, 10 * random.nextDouble)
      val maxCells = 4000
      val sample = SampleUtils.sampleRasterExtent(uberExtent, cs, maxCells)
      assert(uberExtent.covers(sample._1), s"$uberExtent must cover ${sample._1}")
      assert(uberExtent.covers(sample._2), s"$uberExtent must cover ${sample._2}")
      assert(uberExtent.covers(sample._3), s"$uberExtent must cover ${sample._3}")
      assert(uberExtent.covers(sample._4), s"$uberExtent must cover ${sample._4}")
    }
  }

  test("ability to sample for large cellsizes") {
    val e = Extent(504885.0, 3872385.0, 733815.0, 4105515.0)
    val cs = CellSize(479.937106918239, 479.69135802469134)
    val samples = SampleUtils.sampleRasterExtent(e, cs, 255)
    assert(e.contains(samples._1), "overall extent must contain sample extent")
    assert(e.contains(samples._2), "overall extent must contain sample extent")
    assert(e.contains(samples._3), "overall extent must contain sample extent")
    assert(e.contains(samples._4), "overall extent must contain sample extent")
  }

  test("sampling budget should check all 4 corners") {
    val uberExtent = Extent(0, 0, 3, 3)
    val tl = Extent(0, 2, 1, 3)
    val tr = Extent(2, 2, 3, 3)
    val bl = Extent(0, 0, 1, 1)
    val br = Extent(2, 0, 3, 1)
    assert(SampleUtils.sampleRasterExtent(uberExtent, cs = CellSize(1, 1), maxCells = 4) == (tl, tr, bl, br),
      "Expected one unit of cellsize in each corner of Extent"
    )
  }
}
