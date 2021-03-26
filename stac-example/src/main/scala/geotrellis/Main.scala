package geotrellis

import com.azavea.gdal.GDALWarp
import geotrellis.layer.LayoutDefinition
import geotrellis.proj4.{CRS, Transform}
import geotrellis.raster.gdal.GDALDataset.DatasetType
import geotrellis.raster.gdal.{GDALDataset, GDALIOException, GDALRasterSource, acceptableDatasets, numberOfAttempts}
import geotrellis.raster.reproject.ReprojectRasterExtent
import geotrellis.raster.{ArrayTile, ByteCellType, GridBounds, RasterExtent, Tile, TileLayout}
import geotrellis.store.GeoTrellisRasterSource
import geotrellis.vector.Extent

object Main {
  def main(args: Array[String]): Unit = {
    // https://datacube.services.geo.ca/ows/elevation?SERVICE=WMS&
    // VERSION=1.3.0&REQUEST=GetMap&
    // BBOX=-13545.86242142471019,6808610.873036045581,1090481.44127773121,7675087.328320214525&
    // CRS=EPSG:2957&
    // WIDTH=1135&HEIGHT=890&
    // LAYERS=hrdsm&

    GDALRasterSource("")

    val sourceCRS = CRS.fromString("+proj=lcc +lat_1=49 +lat_2=77 +lat_0=49 +lon_0=-95 +x_0=0 +y_0=0 +ellps=GRS80 +towgs84=0,0,0,0,0,0,0 +units=m +no_defs")
    val sourceZoom = 15
    val sourceExtent = Extent(-2328477.302331076, -732242.5105083217, 2641140.7340110596, 3898486.2485002363)
    val sourceLayoutDefinition = LayoutDefinition(
      Extent(-4152858.0, -3698040.0, 4235750.0, 4690568.0),
      TileLayout(32, 32, 256, 256)
    )

    println(s"sourceLayoutDefinition.cellSize: ${sourceLayoutDefinition.cellSize}")

    val sourceRasterExtent = RasterExtent(sourceExtent, sourceLayoutDefinition.cellSize)

    val targetCRS = CRS.fromEpsgCode(2957)
    val extent = Extent(-13545.86242142471019, 6808610.873036045581, 1090481.44127773121, 7675087.328320214525)
    val (cols, rows) = 1135 -> 890
    val rasterExtent = RasterExtent(extent, cols, rows)

    println(s"rasterExtent.cellSize: ${rasterExtent.cellSize}")

    val thisTargetRasterExtent = ReprojectRasterExtent(sourceRasterExtent, sourceCRS, targetCRS)

    val transform = Transform(sourceCRS, targetCRS)
    val backTransform = Transform(targetCRS, sourceCRS)

    {
      val subExtent = thisTargetRasterExtent.extent.intersection(rasterExtent.extent).get
      val targetRasterExtent = thisTargetRasterExtent.createAlignedRasterExtent(subExtent)
      val sourceExtent = targetRasterExtent.extent.reprojectAsPolygon(backTransform, 0.001).getEnvelopeInternal
      val sourceRegion = sourceLayoutDefinition.createAlignedGridExtent(sourceExtent)

      println(sourceRegion)
    }

    {
      val k = Extent(-1660601.1799640926, 6234591.702313175, 2781860.5915472955, 9774981.542054784).reprojectAsPolygon(backTransform, 0.001).getEnvelopeInternal
      println(k)
    }


     // subExtent <- this.extent.intersection(extent)
    //      targetRasterExtent = this.gridExtent.createAlignedRasterExtent(subExtent)
    //      sourceExtent = targetRasterExtent.extent.reprojectAsPolygon(backTransform, 0.001).getEnvelopeInternal
    //      sourceRegion = sourceLayer.metadata.layout.createAlignedGridExtent(sourceExtent)

    // println(res)

  }
}

