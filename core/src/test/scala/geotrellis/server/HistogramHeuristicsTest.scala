package geotrellis.server

import geotrellis.raster._
import geotrellis.vector._

import org.scalatest._

import scala.util.Random


class HistogramHeuristicsTest extends FunSuite with Matchers {
  test("extent sampling") {
    val random = new Random(1337)
    for (x <- 0 to 10000) {
      val scale = scala.math.abs(random.nextInt)
      val randomXMin = random.nextDouble
      val randomYMin = random.nextDouble

      // To avoid 0 width/heights
      val randomXMaxOffset = random.nextInt(1000).toDouble + 1
      val randomYMaxOffset = random.nextInt(1000).toDouble + 1

      val uberExtent = Extent(randomXMin, randomYMin, randomXMin + randomXMaxOffset, randomYMin + randomYMaxOffset)
      val cs = CellSize(random.nextDouble, random.nextDouble)
      val maxCells = 4000
      val sampleExtent = LayerHistogram.sampleRasterExtent(uberExtent, cs, maxCells)
      assert(uberExtent.envelope.contains(sampleExtent))
    }
  }
}
