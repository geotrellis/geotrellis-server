package geotrellis.server

import com.azavea.maml.error._
import cats._
import cats.implicits._
import cats.effect._
import cats.data.{NonEmptyList => NEL}

object TestImplicits {
  implicit def applicativeErrorIO(implicit applicativeIO: Applicative[IO]): ApplicativeError[IO, NEL[MamlError]] =
    new ApplicativeError[IO, NEL[MamlError]] {
      def raiseError[A](e: NEL[MamlError]): IO[A] = IO { throw new Exception(e.map(_.repr).reduce) }

      def handleErrorWith[A](fa: IO[A])(f: NEL[MamlError] => IO[A]): IO[A] = fa

      def pure[A](x: A): IO[A] = applicativeIO.pure(x)

      def ap[A, B](ff:IO[A => B])(fa: IO[A]): IO[B] = applicativeIO.ap(ff)(fa)
    }
}
