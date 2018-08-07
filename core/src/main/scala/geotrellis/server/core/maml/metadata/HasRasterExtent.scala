package geotrellis.server.core.maml.metadata

import geotrellis.vector.Extent
import geotrellis.proj4.CRS
import geotrellis.raster.{RasterExtent, CellSize}
import com.azavea.maml.ast.{Literal, MamlKind}
import cats._
import cats.data.{NonEmptyList => NEL}
import cats.effect._
import simulacrum._

import java.util.UUID


@typeclass trait HasRasterExtents[A] {
  @op("rasterExtents") def rasterExtents(self: A)(implicit t: Timer[IO]): IO[NEL[RasterExtent]]
  @op("crs") def crs(self: A)(implicit t: Timer[IO]): IO[CRS]
}

