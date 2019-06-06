package geotrellis.server

import geotrellis.server.vlm._

import geotrellis.raster._
import geotrellis.raster.io.geotiff.AutoHigherResolution
import geotrellis.proj4._
import geotrellis.spark.tiling._
import geotrellis.contrib.vlm.TargetRegion
import geotrellis.raster.resample.NearestNeighbor
import geotrellis.vector.Extent

import com.azavea.maml.ast._
import com.azavea.maml.ast.codec.tree._
import com.azavea.maml.eval._
import com.azavea.maml.error._
import cats._
import cats.effect._
import cats.data.{NonEmptyList => NEL}
import org.scalatest._

import scala.concurrent.ExecutionContext


class NoDataHandlingTest extends FunSuite with Matchers with TileAsSourceImplicits {
  implicit val cs = cats.effect.IO.contextShift(ExecutionContext.global)

  val expr = Addition(List(RasterVar("t1"), RasterVar("t2")))
  val eval = LayerTms.curried(expr, ConcurrentInterpreter.DEFAULT)

  test("NODATA should be respected - user-defined, integer-based source celltype") {
    val t1 = IntUserDefinedNoDataArrayTile((1 to 100).toArray, 10, 10, IntUserDefinedNoDataCellType(1))
    val t2 = IntUserDefinedNoDataArrayTile((1 to 100).toArray, 10, 10, IntUserDefinedNoDataCellType(1))
    val paramMap = Map("t1" -> t1, "t2" -> t2)
    // We'll sample such that the bottom row (from 56 to 64) are excised from the result
    val res = eval(paramMap, 0, 0, 0).unsafeRunSync
    println("results", res)
    val tileRes = res.toOption.get.band(0)
    assert(tileRes.toArrayDouble.head.isNaN, s"Expected Double.NaN, got ${tileRes.toArrayDouble.head}")
  }

  test("NODATA should be respected - different source celltypes") {
    val t1 = IntUserDefinedNoDataArrayTile((1 to 100).toArray, 10, 10, IntUserDefinedNoDataCellType(1))
    val t2 = DoubleUserDefinedNoDataArrayTile((1 to 100).map(_.toDouble).toArray, 10, 10, DoubleUserDefinedNoDataCellType(2.0))
    val paramMap = Map("t1" -> t1, "t2" -> t2)
    // We'll sample such that the bottom row (from 56 to 64) are excised from the result
    val res = eval(paramMap, 0, 0, 0).unsafeRunSync
    val tileRes = res.toOption.get.band(0)
    assert(tileRes.toArrayDouble.apply(0).isNaN, s"Expected Double.NaN, got ${tileRes.toArrayDouble.head}")
  }
}
