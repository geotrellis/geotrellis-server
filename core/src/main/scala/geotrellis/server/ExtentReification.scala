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

import geotrellis.raster.{ProjectedRaster, MultibandTile, CellSize}
import geotrellis.vector.Extent
import cats.Contravariant
import cats.effect._
import simulacrum._

@typeclass trait ExtentReification[A] {
  @op("extentReification") def extentReification[F[_]](
      self: A
  ): (Extent, CellSize) => F[ProjectedRaster[MultibandTile]]
}

object ExtentReification {
  implicit val contravariantExtentReification
      : Contravariant[ExtentReification] =
    new Contravariant[ExtentReification] {
      def contramap[A, B](
          fa: ExtentReification[A]
      )(f: B => A): ExtentReification[B] =
        new ExtentReification[B] {
          def extentReification[F[_]](
              self: B
          ): (Extent, CellSize) => F[ProjectedRaster[MultibandTile]] =
            fa.extentReification(f(self))
        }
    }
}
