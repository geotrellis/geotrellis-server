package geotrellis.server.http4s

import geotrellis.server.http4s.auth.{User, Rejector}

import org.http4s._
import org.http4s.dsl.Http4sDsl
import cats.effect.IO
import org.http4s.dsl.Http4sDsl
import org.http4s.implicits._


class PingPongService extends Http4sDsl[IO] with Rejector {
  def routes: AuthedService[Either[String, User], IO] = AuthedService {
    case GET -> Root as user => rejectUnauthorized(user)(Ok(s"pong"))
  }
}

