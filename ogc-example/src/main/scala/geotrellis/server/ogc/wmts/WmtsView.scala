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
import geotrellis.server.utils._

import geotrellis.layer.SpatialKey
import geotrellis.raster.Raster
import com.azavea.maml.eval._
import org.http4s.scalaxml._
import org.http4s._
import org.http4s.dsl.Http4sDsl
import org.http4s.circe._
import _root_.io.circe.syntax._
import cats.effect._
import cats.Parallel
import cats.data.Validated.{Invalid, Valid}
import cats.syntax.apply._
import cats.syntax.applicative._
import cats.syntax.functor._
import cats.syntax.flatMap._
import cats.syntax.applicativeError._
import cats.syntax.parallel._
import io.chrisdavenport.log4cats.Logger
import com.github.blemale.scaffeine.{Cache, Scaffeine}
import org.backuity.ansi.AnsiFormatter.FormattedHelper
import org.http4s.headers.`Content-Type`

import scala.concurrent.duration._

import java.net._

class WmtsView[F[_]: Concurrent: Parallel: ApplicativeThrow: Logger](
    wmtsModel: WmtsModel[F],
    serviceUrl: URL
) extends Http4sDsl[F] {
  val logger = Logger[F]

  private val tileCache: Cache[GetTile, Array[Byte]] =
    Scaffeine()
      .recordStats()
      .expireAfterWrite(1.hour)
      .maximumSize(500)
      .build[GetTile, Array[Byte]]()

  def responseFor(req: Request[F]): F[Response[F]] =
    WmtsParams(req.multiParams) match {
      case Invalid(errors) =>
        val msg = ParamError.generateErrorMessage(errors.toList)
        logger.warn(msg) *> BadRequest(msg)

      case Valid(_: GetCapabilities) =>
        logger.debug(ansi"%bold{GetCapabilities: ${req.uri}}") *>
          new CapabilitiesView(wmtsModel, serviceUrl).toXML flatMap (Ok(_))

      case Valid(wmtsReq: GetTile) =>
        logger.debug(ansi"%bold{GetTile: ${req.uri}}")
        val tileCol   = wmtsReq.tileCol
        val tileRow   = wmtsReq.tileRow
        val layerName = wmtsReq.layer

        val res =
          wmtsModel
            .getLayer(wmtsReq)
            .flatMap {
              _.map { layer =>
                val evalWmts = layer match {
                  case sl: SimpleTiledOgcLayer => LayerTms.withCellType(sl, sl.source.cellType)
                  case mas: MapAlgebraTiledOgcLayer =>
                    LayerTms(mas.algebra.pure[F], mas.parameters.pure[F], ConcurrentInterpreter.DEFAULT[F], mas.targetCellType)
                }

                // TODO: remove this once GeoTiffRasterSource would be threadsafe
                // ETA 6/22/2020: we're pretending everything is fine
                val evalHisto = layer match {
                  case sl: SimpleTiledOgcLayer => LayerHistogram.concurrent(sl, 512)
                  case mas: MapAlgebraTiledOgcLayer =>
                    LayerHistogram(mas.algebra.pure[F], mas.parameters.pure[F], ConcurrentInterpreter.DEFAULT[F], 512)
                }

                (evalWmts(0, tileCol, tileRow), evalHisto).parMapN {
                  case (Valid(mbtile), Valid(hists)) => Valid((mbtile, hists))
                  case (Invalid(errs), _)            => Invalid(errs)
                  case (_, Invalid(errs))            => Invalid(errs)
                }.attempt flatMap {
                  case Right(Valid((mbtile, hists))) => // success
                    val extent   = layer.layout.mapTransform(SpatialKey(tileCol, tileRow))
                    val rendered = Raster(mbtile, extent).render(layer.crs, layer.style, wmtsReq.format, hists)
                    tileCache.put(wmtsReq, rendered)
                    Ok(rendered).map(_.putHeaders(`Content-Type`(ToMediaType(wmtsReq.format))))
                  case Right(Invalid(errs)) => // maml-specific errors
                    logger.debug(errs.toList.toString)
                    BadRequest(errs.asJson)
                  case Left(err) => // exceptions
                    logger.error(err.stackTraceString)
                    InternalServerError(err.stackTraceString)
                }
              }.headOption.getOrElse(BadRequest(s"Layer ($layerName) not found"))
            }

        tileCache.getIfPresent(wmtsReq) match {
          case Some(rendered) => Ok(rendered).map(_.putHeaders(`Content-Type`(ToMediaType(wmtsReq.format))))
          case _              => res
        }
    }
}
