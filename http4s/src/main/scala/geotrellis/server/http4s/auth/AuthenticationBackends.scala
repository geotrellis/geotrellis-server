package geotrellis.server.http4s.auth

import geotrellis.server.http4s.Config

import cats._, cats.effect._, cats.implicits._, cats.data._
import com.typesafe.scalalogging.LazyLogging
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

object AuthenticationBackends extends LazyLogging {

  def fromSigningKey(signingKey: String): Kleisli[OptionT[IO, ?], Request[IO], Either[String, User]] = {
    val sk = HMACSHA256.unsafeBuildKey(signingKey.toCharArray map { _.toByte })
    Kleisli(
      req => {
        val message: EitherT[IO, String, User] = for {
          header <- EitherT[IO, String, String](IO(req.headers.get(Authorization).toRight("Couldn't find an Authorization header") map { _.value }))
          verifiedAndParsed <- EitherT[IO, String, JWTMac[HMACSHA256]](
            JWTMac.verifyAndParse[IO, HMACSHA256](header.replace("Bearer ", ""), sk).attempt map {
              case Left(e) => Left(e.getMessage)
              case Right(p) => Right(p)
            }
          )
        } yield { User(verifiedAndParsed.body.expiration.map( _.getNano ).get, verifiedAndParsed.body.subject.get) }
        OptionT.liftF(message.value)
      }
    )
  }

  def fromConfig(conf: Config): Kleisli[OptionT[IO, ?], Request[IO], Either[String, User]] = {
    conf.auth.signingKey match {
      case "REPLACEME" => {
        logger.warn("Signing key not changed from default. Falling back to always successful authentication")
        successful
      }
      case signingKey => {
        logger.debug("Constructing auth middleware from configured signing key")
        fromSigningKey(signingKey)
      }
    }
  }

  val successful: Kleisli[OptionT[IO, ?], Request[IO], Either[String, User]] = {
    logger.debug("Using 'successful' authentication middleware -- all requests will succeed")
    Kleisli(_ => OptionT.liftF(IO(Right(User(1, "Non-authed service")))))
  }

  val failed: Kleisli[OptionT[IO, ?], Request[IO], Either[String, User]] = {
    logger.debug("Using 'failed' authentication middleware -- all requests will fail")
    Kleisli(_ => OptionT.liftF(IO(Left("oh no"))))
  }

  val fromAuthHeader: Kleisli[OptionT[IO, ?], Request[IO], Either[String, User]] = {
    logger.debug("""
      | Using auth header-based authentication with length check --
      | auth headers with length > 10 will succeed")
    """.trim.stripMargin)
    Kleisli(
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
}
