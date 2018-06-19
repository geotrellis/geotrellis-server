package geotrellis.server.cog

import org.http4s._
import org.http4s.dsl.Http4sDsl
import geotrellis.raster._
import geotrellis.raster.render._
import com.typesafe.scalalogging.LazyLogging
import cats.effect._
import cats.implicits._

import scala.math._
import java.net.URI

class CogService extends Http4sDsl[IO] with LazyLogging {

  object OptionalOpacityQueryParamMatcher extends OptionalQueryParamDecoderMatcher[Double]("opacity")

  object UriQueryParamMatcher extends QueryParamDecoderMatcher[URI]("uri")

  implicit val uriQueryParamDecoder: QueryParamDecoder[URI] =
    QueryParamDecoder[String].map(URI.create)

  val emptyTile = IntConstantNoDataArrayTile(Array(0), 1, 1).renderPng()

  def routes: HttpService[IO] = HttpService[IO] {
    case GET -> Root / IntVar(zoom) / IntVar(x) / IntVar(y) :? UriQueryParamMatcher(uri) +& OptionalOpacityQueryParamMatcher(opaque) =>
      val opacity = max(min(opaque.getOrElse(100.0), 100.0), 0.0)
      CogUtils.fetch(uri.toString, zoom, x, y) match {
        case Right(mbtile) =>
          println("right", opacity, zoom, x, y)
          try {
            println("bands", mbtile.bandCount)
          val colored = mbtile.color().map({ color =>
            val current = RGBA(color)
            RGBA(current.red, current.green, current.blue, opacity)
          })
          Ok(colored.renderPng().bytes)
        } catch { case t: Throwable =>
          println("")
          println("")
          println("")
          println(t)
          t.getStackTrace.foreach(println(_))
          Ok("uhoh")
        }
        case Left(err) =>
          println(err)
          logger.debug(err)
          Ok(err)
      }
      //CogView.fetchCroppedTile(uri, zoom, x, y) match {
      //  case Some(png) => Ok(png.bytes)
      //  case None => Ok(emptyTile.bytes)
      //}
  }
}
