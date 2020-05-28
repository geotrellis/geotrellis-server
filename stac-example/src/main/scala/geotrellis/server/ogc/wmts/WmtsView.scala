/*
 * Copyright 2020 Azavea
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

package geotrellis.server.ogc.wmts

import geotrellis.server._
import geotrellis.server.ogc._
import geotrellis.server.ogc.params.ParamError
import geotrellis.server.ogc.wmts.WmtsParams.{GetCapabilities, GetTile}

import com.azavea.maml.eval._
import org.http4s.scalaxml._
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.circe._
import _root_.io.circe.syntax._
import cats.implicits._
import cats.effect._
import cats.data.Validated._
import org.log4s.getLogger
import com.github.blemale.scaffeine.{Cache, Scaffeine}
import org.backuity.ansi.AnsiFormatter.FormattedHelper

import scala.concurrent.duration._

import java.net._

class WmtsView(wmtsModel: WmtsModel, serviceUrl: URL) {
  val logger = getLogger

  private val tileCache: Cache[GetTile, Array[Byte]] =
    Scaffeine()
      .recordStats()
      .expireAfterWrite(1.hour)
      .maximumSize(500)
      .build[GetTile, Array[Byte]]()

  def responseFor(req: Request[IO])(implicit cs: ContextShift[IO]): IO[Response[IO]] = {
      WmtsParams(req.multiParams) match {
        case Invalid(errors) =>
          val msg = ParamError.generateErrorMessage(errors.toList)
          logger.warn(msg)
          BadRequest(msg)

        case Valid(_: GetCapabilities) =>
          logger.debug(ansi"%bold{GetCapabilities: ${req.uri}}")
          Ok(new CapabilitiesView(wmtsModel, serviceUrl).toXML)

        case Valid(wmtsReq: GetTile) =>
          logger.debug(ansi"%bold{GetTile: ${req.uri}}")
          val tileCol = wmtsReq.tileCol
          val tileRow = wmtsReq.tileRow
          val layerName = wmtsReq.layer

          lazy val res = {
            wmtsModel.getLayer(wmtsReq).map { layer =>
              val evalWmts = layer match {
                case sl@SimpleTiledOgcLayer(_, _, _, _, _, _, _, _) =>
                  LayerTms.identity(sl)
                case MapAlgebraTiledOgcLayer(_, _, _, _, parameters, expr, _, _, _) =>
                  LayerTms(IO.pure(expr), IO.pure(parameters), ConcurrentInterpreter.DEFAULT[IO])
              }

              val evalHisto = layer match {
                case sl@SimpleTiledOgcLayer(_, _, _, _, _, _, _, _) =>
                  LayerHistogram.identity(sl, 512)
                case MapAlgebraTiledOgcLayer(_, _, _, _, parameters, expr, _, _, _) =>
                  LayerHistogram(IO.pure(expr), IO.pure(parameters), ConcurrentInterpreter.DEFAULT[IO], 512)
              }

              (evalWmts(0, tileCol, tileRow), evalHisto).parMapN {
                case (Valid(mbtile), Valid(hists)) =>
                  Valid((mbtile, hists))
                case (Invalid(errs), _) =>
                  Invalid(errs)
                case (_, Invalid(errs)) =>
                  Invalid(errs)
              }.attempt flatMap {
                case Right(Valid((mbtile, hists))) => // success
                  val rendered = Render.singleband(mbtile, layer.style, wmtsReq.format, hists)
                  tileCache.put(wmtsReq, rendered)
                  Ok(rendered)
                case Right(Invalid(errs)) => // maml-specific errors
                  logger.debug(errs.toList.toString)
                  BadRequest(errs.asJson)
                case Left(err) => // exceptions
                  logger.error(err.toString)
                  InternalServerError(err.toString)
              }
            }.headOption.getOrElse(BadRequest(s"Layer ($layerName) not found"))
          }

          tileCache.getIfPresent(wmtsReq) match {
            case Some(rendered) => Ok(rendered)
            case _              => res
          }
      }
  }
}
