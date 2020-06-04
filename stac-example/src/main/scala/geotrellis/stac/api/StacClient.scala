/*
 * Copyright 2020 Azavea
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
