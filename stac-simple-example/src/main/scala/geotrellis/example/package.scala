package geotrellis

import cats.{~>, Foldable, FunctorFilter}
import cats.data.NonEmptyList
import cats.effect.IO
import cats.effect.unsafe.IORuntime
import cats.syntax.foldable._
import cats.syntax.option._
import com.azavea.stac4s.{StacAsset, StacItem}
import geotrellis.proj4.CRS
import geotrellis.raster.effects.MosaicRasterSourceIO
import geotrellis.raster.{EmptyName, GridExtent, MosaicRasterSource, RasterSource, SourceName, StringName}
import geotrellis.raster.geotiff.GeoTiffPath
import geotrellis.stac.raster.{StacAssetRasterSource, StacItemAsset}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.matching.Regex

package object example {
  implicit class AssetsMapOps(private val assets: Map[String, StacAsset]) extends AnyVal {
    def select(selector: Regex): Option[StacAsset] = assets.find { case (k, _) => selector.findFirstIn(k).nonEmpty }.map(_._2)
  }

  implicit class StacAssetOps(private val self: StacAsset) extends AnyVal {
    def hrefGDAL(withGDAL: Boolean): String = if (withGDAL) s"gdal+${self.href}" else s"${GeoTiffPath.PREFIX}${self.href}"
    def withGDAL(withGDAL: Boolean): StacAsset = self.copy(href = hrefGDAL(withGDAL))
  }

  implicit class RasterSourcesQueryOps[G[_]: Foldable: FunctorFilter, T <: RasterSource](private val self: G[T]) {
    def attributesByName: Map[String, String] =
      self.foldMap { rs =>
        rs.name match {
          case StringName(sn) => rs.attributes.map { case (k, v) => s"$sn-$k" -> v }
          case EmptyName      => rs.attributes
        }
      }
  }

  implicit class MosaicRasterSourceOps(private val self: MosaicRasterSource.type) extends AnyVal {
    def instance(
      sourcesList: NonEmptyList[RasterSource],
      targetCRS: CRS,
      sourceName: SourceName,
      stacAttributes: Map[String, String]
    ): MosaicRasterSource = {
      val combinedExtent = sourcesList.map(_.extent).toList.reduce(_ combine _)
      val minCellSize = sourcesList.map(_.cellSize).toList.maxBy(_.resolution)
      val combinedGridExtent = GridExtent[Long](combinedExtent, minCellSize)

      new MosaicRasterSource {
        val sources: NonEmptyList[RasterSource] = sourcesList
        val crs: CRS = targetCRS
        def gridExtent: GridExtent[Long] = combinedGridExtent
        val name: SourceName = sourceName

        override val attributes = stacAttributes
      }
    }

    def instance(sourcesList: NonEmptyList[RasterSource], targetCRS: CRS, stacAttributes: Map[String, String]): MosaicRasterSource =
      instance(sourcesList, targetCRS, EmptyName, stacAttributes)

    def fromStacItems(collectionName: SourceName,
                      items: List[StacItem],
                      assetName: Regex,
                      defaultCRS: CRS,
                      withGDAL: Boolean,
                      parallelMosaicEnabled: Boolean
    ): Option[RasterSource] = {
      val sources = StacAssetRasterSource(items, assetName, withGDAL)
      sources match {
        case head :: Nil => head.some
        case head :: tail =>
          val commonCrs = if (sources.flatMap(_.asset.crs).distinct.size == 1) head.crs else defaultCRS
          val reprojectedSources = NonEmptyList.of(head, tail: _*).map(_.reproject(commonCrs))
          val attributes = reprojectedSources.toList.attributesByName

          val mosaicRasterSource =
            if (parallelMosaicEnabled)
              MosaicRasterSourceIO.instance(reprojectedSources, commonCrs, collectionName, attributes)(IORuntime.global)
            else
              MosaicRasterSource.instance(reprojectedSources, commonCrs, collectionName, attributes)

          mosaicRasterSource.some
        case _ => None
      }
    }
  }

  // format: off
  /**
   * Ugly shims:
   *  1. search via Futures backend and produce futures
   *  2. map into IO to compile fs2.Stream
   *  3. convert it back to Future[List[T]]
   */
  // format: on
  implicit class FS2StreamFutureOps[A](private val self: fs2.Stream[Future, A]) extends AnyVal {
    def toStreamIO: fs2.Stream[IO, A] = self.translate(λ[Future ~> IO](future => IO.fromFuture(IO(future))))
    def compileToFutureList(implicit ec: ExecutionContext): Future[List[A]] =
      Future(toStreamIO.compile.toList.unsafeRunSync()(IORuntime.global))
  }

  implicit class StacAssetRasterSourceOps(private val self: StacAssetRasterSource.type) extends AnyVal {
    def apply(items: List[StacItem], assetName: Regex, withGDAL: Boolean): Seq[StacAssetRasterSource] = items.flatMap { item =>
      item.assets
        .select(assetName)
        .map(itemAsset => StacAssetRasterSource(StacItemAsset(itemAsset.withGDAL(withGDAL), item)))
    }
  }
}
