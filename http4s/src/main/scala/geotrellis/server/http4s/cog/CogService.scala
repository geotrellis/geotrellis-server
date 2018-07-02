package geotrellis.server.http4s.cog

import geotrellis.server.core.cog.CogUtils
import geotrellis.server.http4s.auth.{User, Rejector}

import org.http4s._
import org.http4s.dsl.Http4sDsl
import geotrellis.raster._
import geotrellis.raster.render._
import com.typesafe.scalalogging.LazyLogging
import cats._
import cats.data.EitherT
import cats.implicits._
import cats.effect._

import scala.math._
import java.net.URI


class CogService extends Http4sDsl[IO] with LazyLogging with Rejector {

  object OptionalOpacityQueryParamMatcher extends OptionalQueryParamDecoderMatcher[Double]("opacity")

  object UriQueryParamMatcher extends QueryParamDecoderMatcher[URI]("uri")

  implicit val uriQueryParamDecoder: QueryParamDecoder[URI] =
    QueryParamDecoder[String].map(URI.create)

  val emptyTile = IntConstantNoDataArrayTile(Array(0), 1, 1).renderPng()

  def routes: AuthedService[Either[String, User], IO] = AuthedService[Either[String, User], IO] {
    case req @ GET -> Root / IntVar(zoom) / IntVar(x) / IntVar(y) :? UriQueryParamMatcher(uri) +& OptionalOpacityQueryParamMatcher(opaque) as user => rejectUnauthorized(user) {
      val opacity = max(min(opaque.getOrElse(100.0), 100.0), 0.0)
      val tileBytes: EitherT[IO, Throwable, Array[Byte]] = for {
        mbtile      <- EitherT(CogUtils.fetch(uri.toString, zoom, x, y).attempt)
        _           <- EitherT.pure[IO, Throwable](logger.debug(s"uri: $uri; opacity: $opacity; zoom: $zoom, x: $x, y: $y"))
        coloredTile <- EitherT(IO { mbtile.color().map({ clr =>
                                                         val current = RGBA(clr)
                                                         RGBA(current.red, current.green, current.blue, opacity)
                                                       }) }.attempt)
      } yield coloredTile.renderPng().bytes

      tileBytes.value.flatMap({
                                case Right(bytes) =>
                                  Ok(bytes)
                                case Left(err) =>
                                  logger.debug(err.toString)
                                  err.getStackTrace.foreach({ line => logger.debug(line.toString) })
                                  InternalServerError(err.toString)
                              })
    }
  }
}
