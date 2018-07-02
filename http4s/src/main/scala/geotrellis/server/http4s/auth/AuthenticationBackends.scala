package geotrellis.server.http4s.auth

import cats._, cats.effect._, cats.implicits._, cats.data._
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.headers.Authorization
import org.http4s.implicits._

import scala.util.Random

import java.time._

object AuthenticationBackends {
  val successful: Kleisli[OptionT[IO, ?], Request[IO], Either[String, User]] =
    Kleisli(_ => OptionT.liftF(IO(Right(User(100, "Joseph P Morrison")))))

  val failed: Kleisli[OptionT[IO, ?], Request[IO], Either[String, User]] =
    Kleisli(_ => OptionT.liftF(IO(Left("oh no"))))

  val fromAuthHeader: Kleisli[OptionT[IO, ?], Request[IO], Either[String, User]] = Kleisli(
    {
      request => {

        val message = for {
          header <- request.headers.get(Authorization).toRight("Couldn't find an Authorization header")
          token <- {
            if (header.value.replace("Token ", "").length >= 10)
              Right(header.value.replace("Token ", ""))
            else Left("Invalid token")
          }
          message <- Either.catchOnly[NumberFormatException](token.toLong).leftMap(_.toString)
        } yield message
        message.traverse(_ => OptionT.liftF(IO(User(1234, "ljaskdlfjsd"))))
      }
    }
  )
}
