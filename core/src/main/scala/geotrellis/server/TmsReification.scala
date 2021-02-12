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

import geotrellis.raster.{MultibandTile, ProjectedRaster}
import cats.Contravariant

trait TmsReification[F[_], A] {
  def tmsReification(self: A, buffer: Int): (Int, Int, Int) => F[ProjectedRaster[MultibandTile]]
}

object TmsReificiation {
  implicit def contravariantTmsReification[F[_]]: Contravariant[TmsReification[F, *]] =
    new Contravariant[TmsReification[F, *]] {
      def contramap[A, B](fa: TmsReification[F, A])(f: B => A): TmsReification[F, B] = { (self, buffer) =>
        fa.tmsReification(f(self), buffer)
      }
    }
}
