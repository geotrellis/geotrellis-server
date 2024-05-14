package geotrellis.example

import cats.data.NonEmptyList
import cats.effect.unsafe.IORuntime
import cats.syntax.functor._
import cats.syntax.nested._
import cats.syntax.option._
import com.azavea.stac4s.api.client.{SearchFilters, SttpStacClient}
import geotrellis.proj4.WebMercator
import geotrellis.raster.effects.MosaicRasterSourceIO
import geotrellis.raster.{MosaicRasterSource, StringName}
import geotrellis.stac.raster.{StacAssetRasterSource, StacItemAsset}
import geotrellis.vector.Extent
import sttp.client3.UriContext
import sttp.client3.akkahttp._

import scala.concurrent.duration.DurationInt
import scala.concurrent.Await

object MainAkka {
  // async context is good for client
  import scala.concurrent.ExecutionContext.Implicits.global

  def main(args: Array[String]): Unit = {
    val searchFilters = SearchFilters()
    val limit = 10000 // max items length if filter result is too wide
    val assetName = "b0".r
    val withGDAL: Boolean = false
    val defaultCRS = WebMercator
    val parallelMosaicEnabled = false
    val collectionName = StringName("aviris-classic")
    val extent = Extent(0, 0, 180, 180)
    val stacCatalogURI = uri"http://localhost:9090/"

    val backend = AkkaHttpBackend()
    val client = SttpStacClient(backend, stacCatalogURI)

    val source = client
      .search(searchFilters)
      .take(limit)
      .compileToFutureList
      .map(MosaicRasterSource.fromStacItems(collectionName, _, assetName, defaultCRS, withGDAL, parallelMosaicEnabled))

    val result = source.nested
      .map(_.read(extent))
      .value
      .map(_.flatten)
      .map {
        case Some(raster) => println(s"raster.extent: ${raster.extent}")
        case None         => println(s"no rasters found for $extent")
      }

    Await.ready(result, 10.seconds)
  }
}
