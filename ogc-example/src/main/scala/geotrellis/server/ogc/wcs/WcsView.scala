package geotrellis.server.ogc.wcs

import geotrellis.server.ogc.wcs.params._
import geotrellis.server.ogc.wcs.ops._
import geotrellis.server.ogc._

import geotrellis.contrib.vlm.RasterSource
import geotrellis.contrib.vlm.geotiff._
import geotrellis.contrib.vlm.avro._
import geotrellis.raster.histogram.Histogram
import geotrellis.spark.tiling._
import geotrellis.spark._
import geotrellis.proj4._
import geotrellis.raster.render.{ColorMap, ColorRamp, Png}
import geotrellis.raster._
import com.typesafe.scalalogging.LazyLogging
import geotrellis.spark.io.s3.AmazonS3Client
import scalaxb.CanWriteXML
import org.backuity.ansi.AnsiFormatter.FormattedHelper
import org.http4s.scalaxml._
import org.http4s._, org.http4s.dsl.io._, org.http4s.implicits._
import cats._, cats.implicits._
import cats.effect._
import cats.data.Validated

import java.io.File
import java.net._

class WcsView(wcsModel: WcsModel, serviceUrl: URL) extends LazyLogging {

  private def handleError[Result](result: Either[Throwable, Result])(implicit ee: EntityEncoder[IO, Result]) = result match {
    case Right(res) =>
      logger.info("response", res.toString)
      Ok(res)
    case Left(err) =>
      logger.error(s"error: $err", err)
      InternalServerError(err.toString)
  }

  private val getCoverage = new GetCoverage(wcsModel)

  def responseFor(req: Request[IO])(implicit cs: ContextShift[IO]): IO[Response[IO]] = {
    WcsParams(req.multiParams) match {
      case Validated.Invalid(errors) =>
        val msg = WcsParamsError.generateErrorMessage(errors.toList)
        logger.warn(s"""Error parsing parameters: ${msg}""")
        BadRequest(s"""Error parsing parameters: ${msg}""")

      case Validated.Valid(wcsParams) =>
        wcsParams match {
          case p: GetCapabilitiesWcsParams =>
            logger.debug(ansi"%bold{GetCapabilities: $serviceUrl}")
            val result = Operations.getCapabilities(serviceUrl.toString, wcsModel, p)
            logger.debug(result.toString)
            Ok(result)

          case p: DescribeCoverageWcsParams =>
            logger.debug(ansi"%bold{DescribeCoverage: ${req.uri}}")
            for {
              describeCoverage <- IO { Operations.describeCoverage(wcsModel, p) }.attempt
              result <- handleError(describeCoverage)
            } yield {
              logger.debug("describecoverage result", result)
              result
            }

          case p: GetCoverageWcsParams =>
            logger.debug(ansi"%bold{GetCoverage: ${req.uri}}")
            for {
              getCoverage <- IO { getCoverage.build(p) }.attempt
              result <- handleError(getCoverage)
            } yield {
              logger.debug("getcoverage result", result)
              result
            }
        }
    }
  }
}
