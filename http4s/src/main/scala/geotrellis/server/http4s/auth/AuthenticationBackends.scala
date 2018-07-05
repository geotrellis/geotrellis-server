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

  def fromSigningKey(signingKey: String): Kleisli[OptionT[IO, ?], Request[IO], User] = {
    val sk = HMACSHA256.unsafeBuildKey(signingKey.toCharArray map { _.toByte })
    val jwtIO = for {
      claims <- IO(JWTClaims.apply(subject = Some("James Santucci"), expiration = Some(Instant.now.plusSeconds(3600))))
      jwtString <- JWTMac.buildToString[IO, HMACSHA256](claims, sk)
      verified <- JWTMac.verifyFromString[IO, HMACSHA256](jwtString, sk)
    } yield {
      println(s"A nice jwt for you to use: ${jwtString}")
    }
    jwtIO.unsafeRunSync
    Kleisli {
      req => for {
        header            <- OptionT[IO, String](IO { req.headers.get(Authorization).map(_.value) })
        verifiedAndParsed <- OptionT.liftF[IO, JWTMac[HMACSHA256]](
                               JWTMac.verifyAndParse[IO, HMACSHA256](header.replace("Bearer ", ""), sk)
                             )
      } yield User(verifiedAndParsed.body.expiration.map( _.getNano ).get, verifiedAndParsed.body.subject.get)
    }
  }

  def fromConfig(conf: Config): Kleisli[OptionT[IO, ?], Request[IO], User] = {
    println(s"Signing key is: ${conf.auth.signingKey}")
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

  val successful: Kleisli[OptionT[IO, ?], Request[IO], User] =
    Kleisli(_ => OptionT.pure[IO](User(1, "Non-authed service")))

  val failed: Kleisli[OptionT[IO, ?], Request[IO], Either[String, User]] = {
    logger.debug("Using 'failed' authentication middleware -- all requests will fail")
    Kleisli(_ => OptionT.liftF(IO(Left("oh no"))))
  }

  val fromAuthHeader: Kleisli[IO, Request[IO], Either[String, User]] =
    Kleisli { request =>
      val message = for {
        header <- request.headers.get(Authorization).toRight("Couldn't find an Authorization header")
        token <- {
          if (header.value.replace("Token ", "").length >= 10)
            Right(header.value.replace("Token ", ""))
          else Left("Invalid token")
        }
        message <- Either.catchOnly[NumberFormatException](token.toLong).leftMap(_.toString)
      } yield message
      message.traverse(_ => IO(User(1234, "ljaskdlfjsd")))
    }
}
