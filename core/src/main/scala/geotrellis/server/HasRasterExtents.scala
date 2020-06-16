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

package geotrellis.server

import geotrellis.raster.RasterExtent

import cats.Contravariant
import cats.data.{NonEmptyList => NEL}
import cats.effect._

trait HasRasterExtents[F[_], A] {
  def rasterExtents(self: A): F[NEL[RasterExtent]]
}

object HasRasterExtents {
  def apply[F[_], A](implicit ev: HasRasterExtents[F, A]) = ev

  implicit def contravariantHasRasterExtents[F[_]]
      : Contravariant[HasRasterExtents[F, *]] =
    new Contravariant[HasRasterExtents[F, *]] {
      def contramap[A, B](
          fa: HasRasterExtents[F, A]
      )(f: B => A): HasRasterExtents[F, B] =
        new HasRasterExtents[F, B] {
          def rasterExtents(
              self: B
          ): F[NEL[RasterExtent]] =
            fa.rasterExtents(f(self))
        }
    }
}
