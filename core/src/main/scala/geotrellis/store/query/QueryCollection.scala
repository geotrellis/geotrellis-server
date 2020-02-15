package geotrellis.store.query

import higherkindness.droste.Algebra

trait QueryCollection[T, G[_]] {
  val list: G[T]
  def find(query: Query): G[T]

  def algebra: Algebra[QueryF, G[T]]
}