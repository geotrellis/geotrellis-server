package geotrellis.server

import geotrellis.raster._
import geotrellis.vector._

import org.scalatest._

import scala.util.Random


class HistogramHeuristicsTest extends FunSuite with Matchers {
  test("extent sampling") {
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
      val sampleExtent = LayerHistogram.sampleRasterExtent(uberExtent, cs, maxCells)
      assert(uberExtent.contains(sampleExtent) || uberExtent.covers(sampleExtent), "overall extent must contain sample extent")
    }
  }

  test("extent sampling for big cellsizes") {
    val e = Extent(504885.0, 3872385.0, 733815.0, 4105515.0)
    val cs = CellSize(479.937106918239, 479.69135802469134)
    val sampleExtent = LayerHistogram.sampleRasterExtent(e, cs, 255)
    assert(e.contains(sampleExtent), "overall extent must contain sample extent")
  }
}
