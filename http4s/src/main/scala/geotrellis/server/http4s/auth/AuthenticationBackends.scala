package geotrellis.server.http4s.auth

import geotrellis.server.http4s.Config

import cats._, cats.effect._, cats.implicits._, cats.data._
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.headers.Authorization
import org.http4s.implicits._

import tsec.authentication._
import tsec.jws.mac._
import tsec.jwt._
import tsec.mac.jca._

import scala.util.Random

import java.time._

object AuthenticationBackends {
  // TODO: should be some sort of implicit search for some authInfo type I think
  def verifyJwt[F[_]: Sync](signingKey: MacSigningKey[HMACSHA256], bearerToken: String): F[Boolean] = {
    JWTMac.verifyFromStringBool[F, HMACSHA256](bearerToken, signingKey)
  }

  def fromSigningKey(signingKey: String): Kleisli[OptionT[IO, ?], Request[IO], User] = {
    val sk = HMACSHA256.unsafeBuildKey(signingKey.toCharArray map { _.toByte })
    Kleisli(
      (request: Request[IO]) => {
        val message: EitherT[IO, Throwable, User] = for {
          header <- EitherT[IO, Throwable, String](IO(request.headers.get(Authorization).toRight(new Exception("Couldn't find an Authorization header")) map { _.value }))
          verifiedAndParsed <- EitherT[IO, Throwable, JWTMac[HMACSHA256]](
            JWTMac.verifyAndParse[IO, HMACSHA256](header, sk).attempt
          )
        } yield { User(100, verifiedAndParsed.body.subject.getOrElse("abcdefg")) }
        message.toOption
      }
    )
  }

  def fromConfig(conf: Config): Kleisli[OptionT[IO, ?], Request[IO], User] = {
    val authUserO = conf.auth.signingKey map {
      (signingKey: String) => { fromSigningKey(signingKey) }
    }
    authUserO
  }

  val successful: Kleisli[OptionT[IO, ?], Request[IO], User] =
    Kleisli(_ => OptionT.liftF(IO(User(1, "Non-authed service"))))

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
