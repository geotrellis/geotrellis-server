package geotrellis.server.stac.example

import geotrellis.server._
import geotrellis.server.stac._
import geotrellis.server.stac.Implicits._

import cats.data._
import cats.data.Validated._
import cats.effect._
import com.azavea.maml.error.MamlError
import com.softwaremill.sttp.{Response => _, _}
import com.softwaremill.sttp.circe._
import com.softwaremill.sttp.asynchttpclient.cats.AsyncHttpClientCatsBackend
import geotrellis.raster.{io => _, _}
import geotrellis.raster.histogram.Histogram
import geotrellis.raster.render._
import io.circe._
import io.circe.syntax._
import org.http4s.{Http4sLiteralSyntax => _, _}
import org.http4s.dsl.Http4sDsl
import org.http4s.dsl.io._
import org.http4s.circe._
import org.http4s.circe.CirceEntityDecoder._
import org.http4s.headers._

import com.typesafe.scalalogging.LazyLogging
import scala.collection.mutable.{Map => MutableMap}
import java.net.URLDecoder
import java.util.UUID

class StacService(
    implicit backend: SttpBackend[IO, Nothing],
    contextShift: ContextShift[IO]
) extends LazyLogging {

  // These are dummy caches -- in a real service you'd want something more robust
  // than a fully local mutable map, but this is just an example, so :man_shrugging:
  val cache: MutableMap[String, StacItem] = MutableMap.empty
  val histCache: MutableMap[String, List[Histogram[Double]]] = MutableMap.empty

  def sttpBodyAsResponse[T: Encoder](
      resp: Either[String, Either[DeserializationError[Error], T]]
  ): IO[Response[IO]] =
    resp match {
      case Right(deserialized) =>
        deserialized match {
          case Right(catalog) =>
            Ok(catalog.asJson, `Content-Type`(MediaType.application.json))
          case Left(e) =>
            println(e)
            BadRequest(e.original)
        }
      case Left(e) =>
        BadRequest(e)
    }

  object UriQueryParamDecoderMatcher
      extends QueryParamDecoderMatcher[String]("uri")

  val app: HttpApp[IO] = HttpApp[IO] {
    // This mainly exists to show that all the json is good to go -- it's a pretty
    // boring route
    case GET -> Root :? UriQueryParamDecoderMatcher(uri) =>
      sttp
        .get(uri"$uri")
        .response(asJson[StacCatalog])
        .send()
        .flatMap { response =>
          sttpBodyAsResponse(response.body)
        }

    case req @ POST -> Root / "tms" =>
      for {
        body <- req.as[StacItem]
        _ <- IO { cache += ((body.id, body)) }
        resp <- Ok(
          Map("url" -> s"http://localhost:8080/tms/${body.id}/{z}/{x}/{y}").asJson
        )
      } yield resp

    case GET -> Root / "tms" / layerId / IntVar(z) / IntVar(x) / IntVar(y) =>
      ((for {
        stacItem <- OptionT.fromOption[IO](cache.get(layerId))
        stacItemHist <- OptionT.fromOption[IO](histCache.get(s"$layerId-hist")) orElse {
          OptionT.liftF {
            LayerHistogram.identity(stacItem, 2000) map {
              case Valid(hists) =>
                histCache += ((s"$layerId-hist", hists))
                hists
              case Invalid(errs) =>
                throw new Exception(
                  "Could not produce hists despite reasonable efforts"
                )
            }
          }
        }
        eval = LayerTms.identity(stacItem)
        tileValidated <- OptionT.liftF(eval(z, x, y))
        resp <- tileValidated match {
          case Valid(tile) =>
            logger.debug(s"Tile dimensions: ${tile.dimensions}")
            val rescaled = tile.mapBands(
              (idx: Int, band: Tile) =>
                band
                  .normalize(
                    stacItemHist(idx).minValue getOrElse { band.toArray.min.toDouble },
                    stacItemHist(idx).maxValue getOrElse { band.toArray.max.toDouble },
                    0,
                    255
                  )
                  .toArrayTile
            )
            OptionT.liftF(
              Ok(rescaled.renderPng.bytes, `Content-Type`(MediaType.image.png))
            )
          case Invalid(e) =>
            OptionT.liftF(BadRequest(s"Could not produce tile at $z/$x/$y"))
        }
      } yield { resp }).value flatMap {
        case Some(response) =>
          IO.pure { response }
        case None =>
          NotFound()
      }).attempt.flatMap {
        case Right(x) =>
          IO.pure { x }
        case Left(e) =>
          InternalServerError(e.getMessage)
      }
  }
}
