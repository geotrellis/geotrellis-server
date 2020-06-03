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

package geotrellis.server.ogc.wms

import geotrellis.server._
import geotrellis.server.ogc._
import geotrellis.server.ogc.params.ParamError
import geotrellis.server.ogc.wms.WmsParams.{GetCapabilities, GetMap}

import geotrellis.raster.RasterExtent
import geotrellis.raster._
import com.azavea.maml.error._
import com.azavea.maml.eval._
import org.http4s.scalaxml._
import org.http4s.circe._
import org.http4s._
import org.http4s.dsl.io._
import _root_.io.circe.syntax._
import cats.implicits._
import cats.effect._
import cats.data.Validated._
import com.github.blemale.scaffeine.{Cache, Scaffeine}
import org.log4s.getLogger
import org.backuity.ansi.AnsiFormatter.FormattedHelper

import java.net.URL
import scala.concurrent.duration._

class WmsView(wmsModel: WmsModel, serviceUrl: URL) {
  val logger = getLogger

  private val histoCache: Cache[OgcLayer, Interpreted[List[Histogram[Double]]]] =
    Scaffeine()
      .recordStats()
      .expireAfterWrite(1.hour)
      .maximumSize(500)
      .build[OgcLayer, Interpreted[List[Histogram[Double]]]]()

  private val tileCache: Cache[GetMap, Array[Byte]] =
    Scaffeine()
      .recordStats()
      .expireAfterWrite(1.hour)
      .maximumSize(500)
      .build[GetMap, Array[Byte]]()

  def responseFor(req: Request[IO])(implicit cs: ContextShift[IO]): IO[Response[IO]] = {
    WmsParams(req.multiParams) match {
      case Invalid(errors) =>
        val msg = ParamError.generateErrorMessage(errors.toList)
        logger.warn(msg)
        BadRequest(msg)

      case Valid(_: GetCapabilities) =>
        logger.debug(ansi"%bold{GetCapabilities: ${req.uri}}")
        Ok(new CapabilitiesView(wmsModel, serviceUrl).toXML)

      case Valid(wmsReq: GetMap) =>
        logger.debug(ansi"%bold{GetMap: ${req.uri}}")
        val re = RasterExtent(wmsReq.boundingBox, wmsReq.width, wmsReq.height)
        lazy val res = wmsModel.getLayer(wmsReq).map { layer =>
          val evalExtent = layer match {
            case sl @ SimpleOgcLayer(_, _, _, _, _, _, _) =>
              LayerExtent.identity(sl)
            case MapAlgebraOgcLayer(_, _, _, parameters, expr, _, _, _) =>
              LayerExtent(IO.pure(expr), IO.pure(parameters), ConcurrentInterpreter.DEFAULT[IO])
          }

          val evalHisto = layer match {
            case sl @ SimpleOgcLayer(_, _, _, _, _, _, _) =>
              LayerHistogram.identity(sl, 512)
            case MapAlgebraOgcLayer(_, _, _, parameters, expr, _, _, _) =>
              LayerHistogram(IO.pure(expr), IO.pure(parameters), ConcurrentInterpreter.DEFAULT[IO], 512)
          }

          val histIO = for {
            cached <- IO { histoCache.getIfPresent(layer) }
            hist   <- cached match {
                        case Some(h) => IO.pure(h)
                        case None => evalHisto
                      }
            _ <-  IO { histoCache.put(layer, hist) }
          } yield hist

          (evalExtent(re.extent, re.cellSize), histIO).parMapN {
            case (Valid(mbtile), Valid(hists)) =>
              Valid((mbtile, hists))
            case (Invalid(errs), _) =>
              Invalid(errs)
            case (_, Invalid(errs)) =>
              Invalid(errs)
          }.attempt flatMap {
            case Right(Valid((mbtile, hists))) => // success
              val rendered = Render.singleband(mbtile, layer.style, wmsReq.format, hists)
              tileCache.put(wmsReq, rendered)
              Ok(rendered)
            case Right(Invalid(errs)) => // maml-specific errors
              logger.debug(errs.toList.toString)
              BadRequest(errs.asJson)
            case Left(err) =>            // exceptions
              // err.printStackTrace()
              logger.error(err.toString)
              InternalServerError(err.toString)
          }
        }.headOption.getOrElse(wmsReq.layers.headOption match {
          case Some(layerName) =>
            BadRequest(s"Layer (${layerName}) not found or CRS (${wmsReq.crs}) not supported")
          case None =>
            BadRequest(s"Layer not found (no layer name provided in request)")
        })

        tileCache.getIfPresent(wmsReq) match {
          case Some(rendered) => Ok(rendered)
          case _              => res
        }
    }
  }
}
