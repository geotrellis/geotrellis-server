package geotrellis.server.ogc.wms


import org.http4s._
import org.http4s.dsl.Http4sDsl
import org.http4s.dsl.io._
import org.http4s.scalaxml._
import org.http4s.implicits._


import cats.data.Validated
import cats.effect._
import com.typesafe.scalalogging.LazyLogging
import com.typesafe.config.ConfigFactory
import io.circe._
import io.circe.syntax._

import geotrellis.spark.io.AttributeStore
import geotrellis.spark._
import geotrellis.spark.io._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Try
import scala.xml.NodeSeq
import java.net.URI

import geotrellis.server.ogc.params.ParamError
import geotrellis.server.ogc.wms.WmsParams.{GetCapabilities, GetMap}

import scala.xml.NamespaceBinding

object WmsService {
  type MetadataCatalog = Map[String, (Seq[Int], Option[TileLayerMetadata[SpatialKey]])]
}

class WmsService(catalog: URI, authority: String, port: Int) extends Http4sDsl[IO] with LazyLogging {
  def handleError[Result](result: Either[Throwable, Result])(implicit ee: EntityEncoder[IO, Result]): IO[Response[IO]] = result match {
    case Right(res) =>
      logger.trace(res.toString)
      Ok(res)

    case Left(err) =>
      logger.error(err.toString)
      InternalServerError(err.toString)
  }

  def routes: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> Root / "wms" =>
      WmsParams(req.multiParams) match {
        case Validated.Invalid(errors) =>
          val msg = ParamError.generateErrorMessage(errors.toList)
          logger.warn(msg)
          BadRequest(msg)

        case Validated.Valid(wmsReq: GetCapabilities) =>
          Ok.apply(new CapabilitiesView().toXML)

        case Validated.Valid(wmsReq: GetMap) =>
          Ok(s"""GET Request: $wmsReq""")
      }

    case req =>
      logger.warn(s"""Recv'd UNHANDLED request: $req""")
      BadRequest("Don't know what that is")
  }
}
