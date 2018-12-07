package geotrellis.server

import geotrellis.server.extent.SampleUtils

import geotrellis.raster._
import geotrellis.vector._
import cats.implicits._

import org.scalatest._

import scala.util.Random
import scala.concurrent.ExecutionContext


class LayerExtentTest extends FunSuite with Matchers {
  implicit val cs = cats.effect.IO.contextShift(ExecutionContext.global)

  test("ability to read a selected extent") {
    val rt = ResourceTile("8x8.tif")
    val eval = LayerExtent.identity(rt)
    // We'll sample such that the bottom row (from 56 to 64) are excised from the result
    val sampled = eval(Extent(0, 1, 8, 8), CellSize(1, 1)).unsafeRunSync
    val sample = sampled.toOption.get.band(0).toArray()
    val sampleSum = sample.fold(0)(_ + _)
    assert(sampleSum == 1596, s"Expected sum of 1596, got $sampleSum")
  }
}
