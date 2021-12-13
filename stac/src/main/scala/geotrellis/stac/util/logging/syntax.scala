package geotrellis.stac.util.logging

import com.azavea.stac4s.api.client.{StreamingStacClient, StreamingStacClientFS2}
import cats.effect.Sync
import cats.tagless.{ApplyK, Derive}
import fs2.Stream
import tofu.higherKind.Mid

object syntax {

  /** Syntax to attach two [[Mid]] instances (each for a separate param) to a service with two type params. */
  implicit class MidOps3Tuple[U[_[_], _[_], _], F[_], G[_], A](val mfg: (U[Mid[F, *], G, A], U[F, Mid[G, *], A])) extends AnyVal {
    def attach(u: U[F, G, A])(implicit af: ApplyK[U[F, *[_], A]], ag: ApplyK[U[*[_], G, A]]): U[F, G, A] =
      Mid.attach[U[*[_], G, A], F](mfg._1)(Mid.attach[U[F, *[_], A], G](mfg._2)(u))
  }

  implicit class MidOps3TupleReverse[U[_[_], _[_], _], F[_], G[_], A](val mfg: (U[F, Mid[G, *], A], U[Mid[F, *], G, A])) extends AnyVal {
    def attach(u: U[F, G, A])(implicit af: ApplyK[U[F, *[_], A]], ag: ApplyK[U[*[_], G, A]]): U[F, G, A] =
      mfg.swap attach u
  }

  implicit def stacClientApplyKF[F[_]]: ApplyK[StreamingStacClient[*[_], Stream[F, *]]] = Derive.applyK[StreamingStacClient[*[_], Stream[F, *]]]
  implicit def stacClientApplyKG[F[_]]: ApplyK[StreamingStacClient[F, *[_]]]            = Derive.applyK[StreamingStacClient[F, *[_]]]

  implicit class StreamingStacClientOps[F[_]](val self: StreamingStacClientFS2[F]) extends AnyVal {
    def withLogging(implicit sync: Sync[F]): StreamingStacClientFS2[F] = (StacClientLoggingMid[F], StreamingStacClientLoggingMid[F]) attach self
  }
}
