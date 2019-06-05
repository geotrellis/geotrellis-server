package geotrellis.server.stac.example

import geotrellis.server._
import geotrellis.server.stac._
import geotrellis.server.stac.Implicits._

import cats.data._
import cats.data.Validated._
import cats.effect._
import com.azavea.maml.ast._
import com.azavea.maml.ast.codec.tree._
import com.azavea.maml.eval._
import com.softwaremill.sttp.{Response => _, _}
import com.softwaremill.sttp.circe._
import com.softwaremill.sttp.asynchttpclient.cats.AsyncHttpClientCatsBackend
import geotrellis.raster.{io => _, _}
import geotrellis.raster.render._
import io.circe._
import io.circe.syntax._
import org.http4s.{Http4sLiteralSyntax => _, _}
import org.http4s.dsl.Http4sDsl
import org.http4s.dsl.io._
import org.http4s.circe._
import org.http4s.circe.CirceEntityEncoder._
import org.http4s.headers._

import com.typesafe.scalalogging.LazyLogging
import java.net.URLDecoder


class StacService(rootUrl: String)(implicit backend: SttpBackend[IO, Nothing]) extends LazyLogging {
  def sttpBodyAsResponse[T: Encoder](resp: Either[String, Either[DeserializationError[Error], T]]): IO[Response[IO]] =
    resp match {
      case Right(deserialized) =>
        deserialized match {
          case Right(catalog) =>
            Ok(catalog, `Content-Type`(MediaType.application.json))
          case Left(e) =>
            println(e)
            BadRequest(e.original)
        }
      case Left(e) =>
        BadRequest(e)
    }

  val app: HttpApp[IO] = HttpApp[IO] {
    case GET -> Root =>
      sttp
        .get(uri"$rootUrl")
        .response(asJson[StacCatalog])
        .send()
        .flatMap { response =>
          sttpBodyAsResponse(response.body)
        }

  }
}
