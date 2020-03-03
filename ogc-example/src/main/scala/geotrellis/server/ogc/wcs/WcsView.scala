/*
 * Copyright 2019 Azavea
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package geotrellis.server.ogc.wcs

import geotrellis.server.ogc.params.ParamError

import org.backuity.ansi.AnsiFormatter.FormattedHelper
import org.http4s.scalaxml._
import org.http4s._
import org.http4s.dsl.io._
import cats.effect._
import cats.data.Validated

import java.net._

class WcsView(wcsModel: WcsModel, serviceUrl: URL) {
  val logger = org.log4s.getLogger

  private def handleError[Result: EntityEncoder[IO, *]](result: Either[Throwable, Result]): IO[Response[IO]] = result match {
    case Right(res) =>
      logger.info(s"response ${res.toString}")
      Ok(res)
    case Left(err) =>
      logger.error(s"error: $err")
      InternalServerError(err.toString)
  }

  private val getCoverage = new GetCoverage(wcsModel)

  def responseFor(req: Request[IO])(implicit cs: ContextShift[IO]): IO[Response[IO]] = {
    WcsParams(req.multiParams) match {
      case Validated.Invalid(errors) =>
        val msg = ParamError.generateErrorMessage(errors.toList)
        logger.warn(s"""Error parsing parameters: ${msg}""")
        BadRequest(s"""Error parsing parameters: ${msg}""")

      case Validated.Valid(wcsParams) =>
        wcsParams match {
          case p: GetCapabilitiesWcsParams =>
            logger.debug(ansi"%bold{GetCapabilities: $serviceUrl}")
            Ok(new CapabilitiesView(wcsModel, serviceUrl).toXML)

          case p: DescribeCoverageWcsParams =>
            logger.debug(ansi"%bold{DescribeCoverage: ${req.uri}}")
            Ok(CoverageView(wcsModel, serviceUrl, p).toXML)

          case p: GetCoverageWcsParams =>
            logger.debug(ansi"%bold{GetCoverage: ${req.uri}}")
            for {
              getCoverage <- getCoverage.build(p).attempt
              result      <- handleError(getCoverage)
            } yield {
              logger.debug(s"getcoverage result: $result")
              result
            }
        }
    }
  }
}
