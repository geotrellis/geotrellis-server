package geotrellis.server.http4s.auth

import org.http4s._
import org.http4s.dsl.io._

import cats.data._
import cats.effect.IO
import cats.implicits._

trait Rejector {
  def rejectUnauthorized(authResult: Either[String, User])(resp: => IO[Response[IO]]): IO[Response[IO]] =
    authResult match {
      case Right(_) => resp
      case Left(err) => Forbidden(err)
    }
}
