package geotrellis.store.query

import java.time.ZonedDateTime

import geotrellis.raster.RasterSource
import geotrellis.vector.Extent
import higherkindness.droste.data.Fix
import higherkindness.droste.scheme
import higherkindness.droste.syntax.fix._

object Main {
  def main(arg: Array[String]): Unit = {
    val list: List[RasterSource] = Nil

    val queryF: Fix[QueryF] =
      QueryF.And(
        QueryF.And(
          QueryF.Intersects(Extent(0, 0, 2, 2)).fix[QueryF],
          QueryF.Intersects(Extent(1, 1, 4, 4)).fix[QueryF]
        ).fix[QueryF],
        QueryF.At(ZonedDateTime.now()).fix[QueryF]
      ).fix[QueryF]

    val queryF2 = (intersects(Extent(0, 0, 2, 2)) or intersects(Extent(1, 1, 4, 4))) and at(ZonedDateTime.now())

    println(queryF)
    println(queryF2)

    val res = scheme.cata(RasterSourceCollection.algebraList(list))

    println(res(queryF2))

    // val res = QueryF.cata(queryF)(QueryF.listAlg(list))

    // println(res)
  }
}
