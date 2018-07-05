package geotrellis.server.http4s

import geotrellis.server.http4s.auth.User

import org.http4s._
import org.http4s.dsl.Http4sDsl
import cats.effect.IO
import org.http4s.dsl.Http4sDsl
import org.http4s.implicits._


class PingPongService extends Http4sDsl[IO] {
  def routes = AuthedService[User, IO] {
    case GET -> Root as user => Ok(s"pong")
  }
}

