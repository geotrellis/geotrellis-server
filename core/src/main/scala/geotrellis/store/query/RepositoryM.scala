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

import cats.~>
import cats.arrow.FunctionK
import cats.{Applicative, Id}
import cats.syntax.apply._
import cats.syntax.semigroupk._
import cats.{FlatMap, Semigroup, SemigroupK}
import geotrellis.store.query

trait RepositoryM[M[_], G[_], T] { self =>
  def store: M[G[T]] = find(query.all)
  def find(query: Query): M[G[T]]

  def mapK[F[_]](f: M ~> F): RepositoryM[F, G, T] = new RepositoryM[F, G, T] {
    override def find(query: Query): F[G[T]] = f(self.find(query))
  }
}

object RepositoryM {
  implicit def semigroupRepositoryM[M[_]: FlatMap, G[_]: SemigroupK, T]
      : Semigroup[RepositoryM[M, G, T]] = Semigroup.instance {
    (x: RepositoryM[M, G, T], y: RepositoryM[M, G, T]) =>
      new RepositoryM[M, G, T] {

        def find(query: Query): M[G[T]] = x.find(query).map2(y.find(query))(_ <+> _)
      }
  }

  implicit def IdToApplicative[M[_]: Applicative, G[_], T](repository: Repository[G, T]): RepositoryM[M, G, T] =
    repository.mapK(new FunctionK[Id, M] {
      override def apply[A](fa: Id[A]): M[A] = Applicative[M].pure(fa)
    })
}
