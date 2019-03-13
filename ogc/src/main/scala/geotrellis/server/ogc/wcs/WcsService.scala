package geotrellis.server.ogc.wcs

import geotrellis.server.ogc.RasterSourcesModel
import geotrellis.server.ogc.wcs.params._
import geotrellis.server.ogc.wcs.ops._
import geotrellis.server.ogc.ows

import geotrellis.spark.io.AttributeStore
import geotrellis.spark._
import geotrellis.spark.io._
import org.http4s._
import org.http4s.dsl.Http4sDsl
import org.http4s.dsl.io._
import org.http4s.implicits._
import org.http4s.scalaxml._
import cats.data.Validated
import cats.effect._
import com.typesafe.scalalogging.LazyLogging
import com.typesafe.config.ConfigFactory
import _root_.io.circe._
import _root_.io.circe.syntax._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Try
import scala.xml.NodeSeq
import java.net.URL

class WcsService(
  rsm: RasterSourcesModel,
  serviceUrl: URL,
  serviceMetadata: ows.ServiceMetadata
)(implicit cs: ContextShift[IO]) extends Http4sDsl[IO] with LazyLogging {

 def handleError[Result](result: Either[Throwable, Result])(implicit ee: EntityEncoder[IO, Result]) = result match {
   case Right(res) =>
     logger.info("response", res.toString)
     Ok(res)
   case Left(err) =>
     logger.error(s"error: $err", err)
     InternalServerError(err.toString)
 }

 val getCoverage = new GetCoverage(rsm)

 def routes = HttpRoutes.of[IO] {
   case req @ GET -> Root =>
     logger.warn(s"""Recv'd request: $req""")
     WcsParams(req.multiParams) match {
       case Validated.Invalid(errors) =>
         val msg = WcsParamsError.generateErrorMessage(errors.toList)
         logger.warn(s"""Error parsing parameters: ${msg}""")
         Ok(s"""Error parsing parameters: ${msg}""")

       case Validated.Valid(wcsParams) =>
         wcsParams match {
           case p: GetCapabilitiesWcsParams =>
             logger.debug(s"\033[1mGetCapabilities: $serviceUrl\033[0m")
             val result = Operations.getCapabilities(serviceMetadata, serviceUrl.toString, rsm, p)
             logger.debug(result.toString)
             Ok(result)

           case p: DescribeCoverageWcsParams =>
             logger.debug(s"\033[1mDescribeCoverage: ${req.uri}\033[0m")
             for {
               describeCoverage <- IO { Operations.describeCoverage(rsm, p) }.attempt
               result <- handleError(describeCoverage)
             } yield {
               logger.debug("describecoverage result", result)
               result
             }

           case p: GetCoverageWcsParams =>
             logger.debug(s"\033[1mGetCoverage: ${req.uri}\033[0m")
             for {
               getCoverage <- IO { getCoverage.build(p) }.attempt
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
