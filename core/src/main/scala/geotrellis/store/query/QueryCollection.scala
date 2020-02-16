package geotrellis.store.query

trait QueryCollection[T, G[_]] {
  val list: G[T]
  def find(query: Query): G[T]
}