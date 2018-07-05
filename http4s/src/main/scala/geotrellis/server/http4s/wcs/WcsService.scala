package geotrellis.server.http4s.wcs

import org.http4s._
import org.http4s.dsl.Http4sDsl
import org.http4s.dsl.io._
import org.http4s.implicits._
import org.http4s.scalaxml._

import cats.data.Validated
import cats.effect._
import Validated._
import com.typesafe.scalalogging.LazyLogging
import com.typesafe.config.ConfigFactory
import io.circe._
import io.circe.syntax._

import geotrellis.spark.io.AttributeStore
import geotrellis.server.http4s.auth.{Rejector, User}
import geotrellis.server.http4s.wcs.params._
import geotrellis.server.http4s.wcs.ops._
import geotrellis.spark._
import geotrellis.spark.io._

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Try
import scala.xml.NodeSeq
import java.net.URI

object WcsService {
  type MetadataCatalog = Map[String, (Seq[Int], Option[TileLayerMetadata[SpatialKey]])]
}

class WcsService(catalog: URI) extends Http4sDsl[IO] with LazyLogging with Rejector {

  def handleError[Result](result: Either[Throwable, Result])(implicit ee: EntityEncoder[IO, Result]) = result match {
    case Right(res) =>
      logger.info(res.toString)
      Ok(res)
    case Left(err) =>
      logger.error(err.toString)
      InternalServerError(err.toString)
  }

  val catalogMetadata = {
    val as = AttributeStore(catalog)
    logger.info(s"Loading metadata for catalog at ${catalog} ...")
    as.layerIds
      .sortWith{ (a, b) => a.name < b.name || (a.name == b.name && a.zoom > b.zoom) }
      .groupBy(_.name)
      .mapValues(_.map(_.zoom))
      .map { case (name, zooms) =>
        logger.info(s"  -> $name @ zoom=${zooms.head}")
        val metadata = Try(as.readMetadata[TileLayerMetadata[SpatialKey]](LayerId(name, zooms.head))).toOption
        name -> (zooms, metadata)
      }
  }

  val getCoverage = new GetCoverage(catalog.toString)

  def routes: AuthedService[Either[String, User], IO] = AuthedService[Either[String, User], IO] {
    case authedReq @ GET -> Root as user => rejectUnauthorized(user) {
      logger.info(s"Request received: ${authedReq.req.uri}")
      WcsParams(authedReq.req.multiParams) match {
        case Invalid(errors) =>
          val msg = WcsParamsError.generateErrorMessage(errors.toList)
          logger.debug(s"""Error parsing parameters: ${msg}""")
          Ok(s"""Error parsing parameters: ${msg}""")
        case Valid(wcsParams) =>
          wcsParams match {
            case p: GetCapabilitiesWcsParams =>
              val link = s"${authedReq.req.uri.scheme}://${authedReq.req.uri.authority}${authedReq.req.uri.path}?"
              logger.info(s"GetCapabilities request arrived at $link")
              Ok(GetCapabilities.build(link, catalogMetadata, p))
            case p: DescribeCoverageWcsParams =>
              logger.info(s"DescribeCoverage request arrived at ${authedReq.req.uri}")
              for {
                getCoverage <- IO { DescribeCoverage.build(catalogMetadata, p) }.attempt
                result <- handleError(getCoverage)
              } yield {
                logger.info("describecoverage result", result)
                result
              }
            case p: GetCoverageWcsParams =>
              logger.info(s"GetCoverage request arrived at ${authedReq.req.uri}")
              for {
                getCoverage <- IO { getCoverage.build(catalogMetadata, p) }.attempt
                result <- handleError(getCoverage)
              } yield {
                logger.info("getcoverage result", result)
                result
              }
          }
      }
    }
  }
}
