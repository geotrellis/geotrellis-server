package geotrellis.server

import geotrellis.server.extent.SampleUtils

import geotrellis.raster._
import geotrellis.vector._
import cats.implicits._

import org.scalatest._

import scala.util.Random
import scala.concurrent.ExecutionContext


class LayerHistogramTest extends FunSuite with Matchers {
  implicit val cs = cats.effect.IO.contextShift(ExecutionContext.global)

  test("extents sampled from within overall extent") {
    val rt = ResourceTile("8x8.tif")
    val samples = LayerHistogram.identity(rt, 4).unsafeRunSync
    val sampleCount = samples.toOption.get.head.statistics.get.dataCells
    assert(sampleCount == 4, s"Expected 4 cells in histogram, got $sampleCount")
  }

  test("histogram samples the total extent when budget is equal to the cell count") {
    val rt = ResourceTile("8x8.tif")
    val samples = LayerHistogram.identity(rt, 64).unsafeRunSync
    val sampleCount = samples.toOption.get.head.statistics.get.dataCells
    assert(sampleCount == 64, s"Expected 64 cells in histogram, got $sampleCount")
  }

  test("histogram samples the total extent when budget too big") {
    val rt = ResourceTile("8x8.tif")
    val samples = LayerHistogram.identity(rt, 128).unsafeRunSync
    val sampleCount = samples.toOption.get.head.statistics.get.dataCells
    assert(sampleCount == 64, s"Expected 64 cells in histogram, got $sampleCount")
  }
}
