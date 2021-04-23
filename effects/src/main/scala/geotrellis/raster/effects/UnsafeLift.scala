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

import cats.Id
import cats.effect.IO

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

/** Type class that allows to handle unsafe calls */
trait UnsafeLift[F[_]] {
  def apply[A](value: => A): F[A]
}

object UnsafeLift {
  def apply[F[_]: UnsafeLift]: UnsafeLift[F] = implicitly[UnsafeLift[F]]

  implicit val liftId: UnsafeLift[Id] = new UnsafeLift[Id] {
    def apply[A](value: => A): Id[A] = value
  }

  implicit val liftIO: UnsafeLift[IO] = new UnsafeLift[IO] {
    def apply[A](value: => A): IO[A] = IO(value)
  }

  implicit def liftFuture(implicit ec: ExecutionContext): UnsafeLift[Future] = new UnsafeLift[Future] {
    def apply[A](value: => A): Future[A] = Future(value)
  }

  implicit val liftOption: UnsafeLift[Option] = new UnsafeLift[Option] {
    def apply[A](value: => A): Option[A] = Try(value).toOption
  }

  implicit val liftEither: UnsafeLift[Either[Throwable, *]] = new UnsafeLift[Either[Throwable, *]] {
    def apply[A](value: => A): Either[Throwable, A] = Try(value) match {
      case Success(value) => Right(value)
      case Failure(e)     => Left(e)
    }
  }

  implicit val liftTry: UnsafeLift[Try] = new UnsafeLift[Try] {
    def apply[A](value: => A): Try[A] = Try(value)
  }
}
