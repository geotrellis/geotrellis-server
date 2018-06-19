package geotrellis.server

import org.http4s._
import org.http4s.dsl.Http4sDsl
import cats.effect.IO
import org.http4s.dsl.Http4sDsl


class PingPongService extends Http4sDsl[IO] {
  def routes: HttpService[IO] = HttpService[IO] {
    case GET -> Root => Ok("pong")
  }
}

