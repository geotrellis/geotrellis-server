package geotrellis.server

import geotrellis.proj4.CRS
import geotrellis.raster.RasterExtent

import cats.data.{NonEmptyList => NEL}
import cats.effect._

import simulacrum._

@typeclass trait HasRasterExtents[A] {
  @op("rasterExtents") def rasterExtents[F[_]](self: A)(implicit F: ConcurrentEffect[F]): F[NEL[RasterExtent]]
}

