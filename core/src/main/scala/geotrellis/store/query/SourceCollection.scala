package geotrellis.store.query

import geotrellis.raster.RasterSource

trait SourceCollection {
  val list: List[RasterSource]

  def find(query: Query): List[RasterSource] = QueryF.eval(query)(list)
}