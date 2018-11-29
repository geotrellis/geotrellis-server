package geotrellis.server

import cats.effect.IO

package object vlm {
  implicit class OptionMethods[T](opt: Option[T]) {
    def toIO[E <: Throwable](e: => E): IO[T] = opt.fold(IO.raiseError[T](e))(IO.pure)
  }
}
