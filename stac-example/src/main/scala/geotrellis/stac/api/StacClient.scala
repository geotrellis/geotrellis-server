package geotrellis.stac.api

import com.azavea.stac4s.{StacCollection, StacItem}
import eu.timepit.refined.types.string.NonEmptyString

trait StacClient[F[_]] {
  def search(filter: SearchFilters = SearchFilters()): F[List[StacItem]]
  def collections: F[List[StacCollection]]
  def collection(collectionId: NonEmptyString): F[Option[StacCollection]]
  def items(collectionId: NonEmptyString): F[List[StacItem]]
  def item(collectionId: NonEmptyString, itemId: NonEmptyString): F[Option[StacItem]]
}
