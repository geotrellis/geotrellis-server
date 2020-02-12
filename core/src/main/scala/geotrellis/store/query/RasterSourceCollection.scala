package geotrellis.store.query

import geotrellis.raster.RasterSource

case class RasterSourceCollection(list: List[RasterSource]) extends QueryCollection[RasterSource] {
  def find(query: Query): List[RasterSource] = QueryF.eval(query)(list)
}
