/*
 * Copyright 2021 Azavea
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

package geotrellis.raster.effects

import cats.effect.{Blocker, ContextShift}
import cats.{FlatMap, Monad, Parallel, Traverse}
import cats.syntax.flatMap._

package object syntax {
  implicit class ContextShiftOps[F[_]: FlatMap: ContextShift, A](val fa: F[A]) {
    def shift: F[A] = ContextShift[F].shift >> fa
  }

  implicit class UnsafeLiftOps[T](t: => T) {
    def lift[F[_]: UnsafeLift]: F[T]                         = UnsafeLift[F].apply(t)
    def lifts[F[_]: FlatMap: UnsafeLift: ContextShift]: F[T] = ContextShift[F].shift >> lift[F]
  }

  implicit class ParallelTraversableBlockerOps[T[_], A](val ta: T[A]) extends AnyVal {
    def parTraverseBlocking[M[_]: Monad: ContextShift, B](f: A => M[B])(implicit T: Traverse[T], P: Parallel[M], B: Blocker): M[T[B]] =
      Parallel.parTraverse(ta)(a => B.blockOn(f(a)))
  }
}
