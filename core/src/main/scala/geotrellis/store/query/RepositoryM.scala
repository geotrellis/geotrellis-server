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

package geotrellis.store.query

import cats.syntax.apply._
import cats.syntax.semigroupk._
import cats.{FlatMap, Semigroup, SemigroupK}
import geotrellis.store.query

trait RepositoryM[M[_], G[_], T] {
  def store: M[G[T]] = find(query.all)
  def find(query: Query): M[G[T]]
}

object RepositoryM {
  implicit def semigroupRepositoryM[M[_]: FlatMap, G[_]: SemigroupK, T]
      : Semigroup[RepositoryM[M, G, T]] = Semigroup.instance {
    (x: RepositoryM[M, G, T], y: RepositoryM[M, G, T]) =>
      new RepositoryM[M, G, T] {

        def find(query: Query): M[G[T]] = x.find(query).map2(y.find(query))(_ <+> _)
      }

  }
}
