package geotrellis.server.wcs

import org.http4s._
import org.http4s.dsl.Http4sDsl
import org.http4s.dsl.io._
import org.http4s.implicits._
import org.http4s.scalaxml._

import cats.data.Validated
import cats.effect._
import com.typesafe.scalalogging.LazyLogging
import com.typesafe.config.ConfigFactory
import io.circe._
import io.circe.syntax._

import geotrellis.spark.io.AttributeStore
import geotrellis.server.wcs.params._
import geotrellis.server.wcs.ops._
import geotrellis.spark._
import geotrellis.spark.io._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Try
import scala.xml.NodeSeq
import java.net.URI

object WcsService {
 type MetadataCatalog = Map[String, (Seq[Int], Option[TileLayerMetadata[SpatialKey]])]
}

class WcsService(catalog: URI, authority: String, port: Int) extends Http4sDsl[IO] with LazyLogging {

 def handleError[Result](result: Either[Throwable, Result])(implicit ee: EntityEncoder[IO, Result]) = result match {
   case Right(res) =>
     logger.info(res.toString)
     Ok(res)
   case Left(err) =>
     logger.error(err.toString)
     InternalServerError(err.toString)
 }

 val catalogMetadata: MetadataCatalog = {
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

 def routes = HttpRoutes.of[IO] {
   case req @ GET -> Root / "wcs" =>
     logger.warn(s"""Recv'd request: $req""")
     WcsParams(req.multiParams) match {
       case Validated.Invalid(errors) =>
         val msg = WcsParamsError.generateErrorMessage(errors.toList)
         logger.warn(s"""Error parsing parameters: ${msg}""")
         Ok(s"""Error parsing parameters: ${msg}""")

       case Validated.Valid(wcsParams) =>
         wcsParams match {
           case p: GetCapabilitiesWcsParams =>
             val link = s"http://${authority}:${port}${req.uri.path}?"
             logger.debug(s"\033[1mGetCapabilities: $link\033[0m")
             val result = Operations.getCapabilities(link, catalogMetadata, p)
             logger.debug(result.toString)
             Ok(result)

           case p: DescribeCoverageWcsParams =>
             logger.debug(s"\033[1mDescribeCoverage: ${req.uri}\033[0m")
             for {
               describeCoverage <- IO { Operations.describeCoverage(catalogMetadata, p) }.attempt
               result <- handleError(describeCoverage)
             } yield {
               logger.debug("describecoverage result", result)
               result
             }

           case p: GetCoverageWcsParams =>
             logger.debug(s"\033[1mGetCoverage: ${req.uri}\033[0m")
             for {
               getCoverage <- IO { getCoverage.build(catalogMetadata, p) }.attempt
               result <- handleError(getCoverage)
             } yield {
               logger.debug("getcoverage result", result)
               result
             }
         }
     }
   case req =>
     logger.warn(s"""Recv'd UNHANDLED request: $req""")
     BadRequest()
 }
}
