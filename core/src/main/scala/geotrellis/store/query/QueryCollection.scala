package geotrellis.store.query

trait QueryCollection[T] {
  val list: List[T]
  def find(query: Query): List[T]
}