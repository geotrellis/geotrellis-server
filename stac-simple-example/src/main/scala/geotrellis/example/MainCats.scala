package geotrellis.example

import cats.data.NonEmptyList
import cats.effect.{ExitCode, IO, IOApp}
import com.azavea.stac4s.api.client.{SearchFilters, SttpStacClient}
import geotrellis.proj4.WebMercator
import geotrellis.raster.{MosaicRasterSource, StringName}
import geotrellis.raster.effects.MosaicRasterSourceIO
import geotrellis.stac.raster.{StacAssetRasterSource, StacItemAsset}
import geotrellis.vector.Extent
import sttp.client3.asynchttpclient.cats.AsyncHttpClientCatsBackend
import sttp.client3.UriContext
import cats.syntax.option._
import cats.syntax.nested._
import cats.syntax.functor._

object MainCats extends IOApp {

  def run(args: List[String]): IO[ExitCode] = {
    val searchFilters = SearchFilters()
    val limit = 10000 // max items length if filter result is too wide
    val assetName = "b0".r
    val withGDAL: Boolean = false
    val defaultCRS = WebMercator
    val parallelMosaicEnabled = false
    val collectionName = StringName("aviris-classic")
    val extent = Extent(0, 0, 180, 180)
    val stacCatalogURI = uri"http://localhost:9090/"

    AsyncHttpClientCatsBackend
      .resource[IO]()
      .use { backend =>
        val client = SttpStacClient(backend, stacCatalogURI)
        client
          .search(searchFilters)
          .take(limit)
          .compile
          .toList
          .map(MosaicRasterSource.fromStacItems(collectionName, _, assetName, defaultCRS, withGDAL, parallelMosaicEnabled))
      }
      .nested
      .map(_.read(extent))
      .value
      .map(_.flatten)
      .map {
        case Some(raster) => println(s"raster.extent: ${raster.extent}")
        case None         => println(s"no rasters found for $extent")
      }
      .as(ExitCode.Success)
  }
}
