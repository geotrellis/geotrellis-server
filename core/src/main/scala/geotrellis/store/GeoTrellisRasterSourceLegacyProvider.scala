
package geotrellis.store

import geotrellis.raster.RasterSourceProvider
import geotrellis.raster.geotiff.GeoTiffPath

class GeoTrellisRasterSourceLegacyProvider extends RasterSourceProvider {
  def canProcess(path: String): Boolean = {
    (!path.startsWith(GeoTiffPath.PREFIX) && !path.startsWith("gdal+")) &&
      path.nonEmpty &&
      GeoTrellisPath.parseOption(path).nonEmpty
  }

  def rasterSource(path: String): GeoTrellisRasterSourceLegacy = new GeoTrellisRasterSourceLegacy(path)
}