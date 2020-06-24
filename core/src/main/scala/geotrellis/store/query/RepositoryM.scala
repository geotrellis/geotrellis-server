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

import cats.{~>, Applicative, Id, Monoid, MonoidK, SemigroupK}
import cats.syntax.apply._
import cats.syntax.semigroupk._

trait RepositoryM[M[_], G[_], T] { self =>
  def store: M[G[T]]
  def find(query: Query): M[G[T]]

  def mapK[F[_]](f: M ~> F): RepositoryM[F, G, T] =
    new RepositoryM[F, G, T] {
      def store: F[G[T]]              = f(self.store)
      def find(query: Query): F[G[T]] = f(self.find(query))
    }
}

object RepositoryM {
  def empty[M[_]: Applicative, G[_]: MonoidK, T]: RepositoryM[M, G, T] =
    new RepositoryM[M, G, T] {
      def store: M[G[T]]              = Applicative[M].pure(MonoidK[G].empty)
      def find(query: Query): M[G[T]] = store
    }

  implicit def repositoryMMonoid[M[_]: Applicative, G[_]: MonoidK: SemigroupK, T]: Monoid[RepositoryM[M, G, T]] =
    new Monoid[RepositoryM[M, G, T]] {
      def empty: RepositoryM[M, G, T]                                                     = RepositoryM.empty[M, G, T]
      def combine(l: RepositoryM[M, G, T], r: RepositoryM[M, G, T]): RepositoryM[M, G, T] =
        new RepositoryM[M, G, T] {
          def store: M[G[T]]              = l.store.map2(r.store)(_ <+> _)
          def find(query: Query): M[G[T]] = l.find(query).map2(r.find(query))(_ <+> _)
        }
    }

  implicit def repositoryMIdToApplicative[M[_]: Applicative, G[_], T](repository: RepositoryM[Id, G, T]): RepositoryM[M, G, T] =
    repository.mapK(λ[Id ~> M](Applicative[M].pure(_)))
}
