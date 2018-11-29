package geotrellis.server.vlm

import geotrellis.contrib.vlm.RasterSource
import geotrellis.proj4.{CRS, WebMercator}
import geotrellis.raster.{CellType, MultibandTile, Raster, RasterExtent}
import geotrellis.raster.resample.{NearestNeighbor, ResampleMethod}
import geotrellis.spark.SpatialKey
import geotrellis.spark.tiling.{LayoutDefinition, ZoomedLayoutScheme}

import cats.effect.IO
import cats.data.{NonEmptyList => NEL}
import io.circe.{Decoder, Encoder}

import java.net.URI

trait RasterSourceUtils {
  implicit val cellTypeEncoder: Encoder[CellType] = Encoder.encodeString.contramap[CellType](CellType.toName)
  implicit val cellTypeDecoder: Decoder[CellType] = Decoder[String].emap { name => Right(CellType.fromName(name)) }

  implicit val uriEncoder: Encoder[URI] = Encoder.encodeString.contramap[URI](_.toString)
  implicit val uriDecoder: Decoder[URI] = Decoder[String].emap { str => Right(URI.create(str)) }

  def getRasterSource(uri: String): RasterSource

  // the target CRS
  val crs: CRS = WebMercator

  val tmsLevels: Array[LayoutDefinition] = {
    val scheme = ZoomedLayoutScheme(crs, 256)
    for (zoom <- 0 to 64) yield scheme.levelForZoom(zoom).layout
  }.toArray

  def fetchTile(uri: String, zoom: Int, x: Int, y: Int, crs: CRS = WebMercator, method: ResampleMethod = NearestNeighbor): IO[Raster[MultibandTile]] = {
    val key = SpatialKey(x, y)
    val ld = tmsLevels(zoom)
    val rs = getRasterSource(uri).reproject(crs, method).tileToLayout(ld, method)

    rs.read(key) match {
      case Some(t) => IO.pure(Raster(t, ld.mapTransform(key)))
      case _ => IO.raiseError(new Exception(s"No Tile availble for the following SpatialKey: ${x}, ${y}"))
    }
  }

  def getCRS(uri: String): IO[CRS] = IO { getRasterSource(uri).crs }
  def getRasterExtents(uri: String): IO[NEL[RasterExtent]]
}
