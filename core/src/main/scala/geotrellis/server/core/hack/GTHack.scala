package geotrellis.hack

import geotrellis.raster._
import geotrellis.raster.io.geotiff._
import geotrellis.raster.io.geotiff.OverviewStrategy

object GTHack {
  def closestTiffOverview[T <: CellGrid](
    tiff: GeoTiff[T],
    cs: CellSize,
    strategy: OverviewStrategy
  ): GeoTiff[T] = tiff.getClosestOverview(cs, strategy)
}
