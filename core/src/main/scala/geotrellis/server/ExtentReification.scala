package geotrellis.server

import geotrellis.raster.{ProjectedRaster, MultibandTile, CellSize}
import geotrellis.vector.Extent
import cats._
import cats.data.EitherT
import cats.effect._
import simulacrum._

import java.util.UUID


@typeclass trait ExtentReification[A] {
  @op("extentReification") def extentReification(self: A)(implicit contextShift: ContextShift[IO]): (Extent, CellSize) => IO[ProjectedRaster[MultibandTile]]
}

