package geotrellis.server.ogc.wms

import io.circe.syntax._
import cats.data.Validated.{Invalid, Valid}
import cats.effect.{Concurrent, Sync}
import cats.{ApplicativeThrow, Parallel}
import cats.syntax.functor._
import cats.syntax.applicative._
import cats.syntax.parallel._
import cats.syntax.flatMap._
import cats.syntax.option._
import cats.syntax.applicativeError._
import com.azavea.maml.error.Interpreted
import io.chrisdavenport.log4cats.Logger
import com.azavea.maml.eval.ConcurrentInterpreter
import geotrellis.raster._
import geotrellis.server.{LayerExtent, LayerHistogram}
import geotrellis.server.ogc._
import geotrellis.server.ogc.wms.WmsParams.GetMapParams
import geotrellis.server.utils.throwableExtensions
import geotrellis.store.query.withName
import com.github.blemale.scaffeine.Cache

case class GetMap[F[_]: Logger: Parallel: Concurrent: ApplicativeThrow](
  model: WmsModel[F],
  tileCache: Cache[GetMapParams, Array[Byte]],
  histoCache: Cache[OgcLayer, Interpreted[List[Histogram[Double]]]]
) {
  def build(params: GetMapParams): F[Either[GetMapException, Array[Byte]]] = {
    val re  = RasterExtent(params.boundingBox, params.width, params.height)
    val res: F[Either[GetMapException, Array[Byte]]] = model
      .getLayer(params)
      .flatMap { layers =>
        layers
          .map { layer =>
            val evalExtent = layer match {
              case sl: SimpleOgcLayer    => LayerExtent.concurrent(sl)
              case ml: MapAlgebraOgcLayer =>
                LayerExtent(ml.algebra.pure[F], ml.parameters.pure[F], ConcurrentInterpreter.DEFAULT[F])
            }

            val evalHisto = layer match {
              case sl: SimpleOgcLayer => LayerHistogram.concurrent(sl, 512)
              case ml: MapAlgebraOgcLayer =>
                LayerHistogram(ml.algebra.pure[F], ml.parameters.pure[F], ConcurrentInterpreter.DEFAULT[F], 512)
            }

            // TODO: remove this once GeoTiffRasterSource would be threadsafe
            // ETA 6/22/2020: we're pretending everything is fine
            val histIO = for {
              cached <- Sync[F].delay { histoCache.getIfPresent(layer) }
              hist   <- cached match {
                case Some(h) => h.pure[F]
                case None    => evalHisto
              }
              _      <- Sync[F].delay { histoCache.put(layer, hist) }
            } yield hist

            val res: F[Either[GetMapException, Array[Byte]]] = (evalExtent(re.extent, re.cellSize.some), histIO).parMapN {
              case (Valid(mbtile), Valid(hists)) => Valid((mbtile, hists))
              case (Invalid(errs), _)            => Invalid(errs)
              case (_, Invalid(errs))            => Invalid(errs)
            }.attempt flatMap {
              case Right(Valid((mbtile, hists))) => // success
                val rendered = Raster(mbtile, re.extent).render(params.crs, layer.style, params.format, hists)
                tileCache.put(params, rendered)
                Right(rendered).pure[F].widen
              case Right(Invalid(errs))          => // maml-specific errors
                Logger[F].debug(errs.toList.toString).as(Left(GetMapBadRequest(errs.asJson.spaces2))).widen
              case Left(err)                     => // exceptions
                Logger[F].error(err.stackTraceString).as(Left(GetMapInternalServerError(err.stackTraceString))).widen
            }

            res
          }
          .headOption
          .getOrElse(params.layers.headOption match {
            case Some(layerName) =>
              // Handle the case where the STAC item was requested for some area between the tiles.
              // STAC search will return an empty list, however QGIS may expect a test pixel to
              // return the actual tile
              // TODO: is there a better way to handle it?
              model.sources
                .find(withName(layerName))
                .flatMap {
                  _.headOption match {
                    case Some(_) =>
                      val tile   = ArrayTile(Array(0, 0), 1, 1)
                      val raster = Raster(MultibandTile(tile, tile, tile), params.boundingBox)
                      Right(raster.render(params.crs, None, params.format, Nil)).pure[F].widen
                    case _       =>
                      Left(GetMapBadRequest(s"Layer ($layerName) not found or CRS (${params.crs}) not supported")).pure[F].widen
                  }
                }
            case None            =>
              Left(GetMapBadRequest(s"Layer not found (no layer name provided in request)")).pure[F].widen
          })
      }

    tileCache.getIfPresent(params) match {
      case Some(rendered) => Right(rendered).pure[F].widen
      case _              => res
    }
  }
}
