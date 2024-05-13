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

package geotrellis.stac.util.logging

import com.azavea.stac4s.api.client.{StreamingStacClient, StreamingStacClientFS2}
import cats.effect.Sync
import cats.tagless.{ApplyK, Derive}
import fs2.Stream
import tofu.higherKind.Mid

object syntax {

  /**
   * Syntax to attach two [[Mid]] instances (each for a separate param) to a service with two type params.
   */
  implicit class MidOps3Tuple[U[_[_], _[_], _], F[_], G[_], A](val mfg: (U[Mid[F, *], G, A], U[F, Mid[G, *], A])) extends AnyVal {
    def attach(u: U[F, G, A])(implicit af: ApplyK[U[F, *[_], A]], ag: ApplyK[U[*[_], G, A]]): U[F, G, A] =
      Mid.attach[U[*[_], G, A], F](mfg._1)(Mid.attach[U[F, *[_], A], G](mfg._2)(u))
  }

  implicit class MidOps3TupleReverse[U[_[_], _[_], _], F[_], G[_], A](val mfg: (U[F, Mid[G, *], A], U[Mid[F, *], G, A])) extends AnyVal {
    def attach(u: U[F, G, A])(implicit af: ApplyK[U[F, *[_], A]], ag: ApplyK[U[*[_], G, A]]): U[F, G, A] =
      mfg.swap.attach(u)
  }

  implicit def stacClientApplyKF[F[_]]: ApplyK[StreamingStacClient[*[_], Stream[F, *]]] = Derive.applyK[StreamingStacClient[*[_], Stream[F, *]]]
  implicit def stacClientApplyKG[F[_]]: ApplyK[StreamingStacClient[F, *[_]]] = Derive.applyK[StreamingStacClient[F, *[_]]]

  implicit class StreamingStacClientOps[F[_]](val self: StreamingStacClientFS2[F]) extends AnyVal {
    def withLogging(implicit sync: Sync[F]): StreamingStacClientFS2[F] = (StacClientLoggingMid[F], StreamingStacClientLoggingMid[F]).attach(self)
  }
}
