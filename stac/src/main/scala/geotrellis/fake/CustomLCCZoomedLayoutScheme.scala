package geotrellis.fake

import geotrellis.proj4.util.UTM
import geotrellis.proj4.{CRS, LatLng}
import geotrellis.raster.{CellSize, TileLayout}
import geotrellis.layer.{LayoutDefinition, LayoutLevel, LayoutScheme}
import geotrellis.util.Haversine
import geotrellis.vector._

object CustomLCCZoomedLayoutScheme {
  //  val EARTH_CIRCUMFERENCE = 2 * math.Pi * Haversine.EARTH_RADIUS
  val EARTH_CIRCUMFERENCE = 7589376d
  val DEFAULT_TILE_SIZE = 256
  val DEFAULT_RESOLUTION_THRESHOLD = 0.1

  def layoutColsForZoom(level: Int): Int = math.pow(2, level).toInt
  def layoutRowsForZoom(level: Int): Int = math.pow(2, level).toInt


  def apply(crs: CRS, tileSize: Int = DEFAULT_TILE_SIZE, resolutionThreshold: Double = DEFAULT_RESOLUTION_THRESHOLD) =
    new CustomLCCZoomedLayoutScheme(crs, tileSize, resolutionThreshold)

  def layoutForZoom(zoom: Int, layoutExtent: Extent, tileSize: Int = DEFAULT_TILE_SIZE): LayoutDefinition = {
    if(zoom < 0)
      sys.error("TMS Tiling scheme does not have levels below 0")
    LayoutDefinition(layoutExtent, TileLayout(layoutColsForZoom(zoom), layoutRowsForZoom(zoom), tileSize, tileSize))
  }
}


class CustomLCCZoomedLayoutScheme(val crs: CRS, val tileSize: Int, val resolutionThreshold: Double) extends LayoutScheme {
  import CustomLCCZoomedLayoutScheme.{EARTH_CIRCUMFERENCE, layoutColsForZoom, layoutRowsForZoom}

  /** This will calcluate the closest zoom level based on the resolution in a UTM zone containing the point.
   * The calculated zoom level is up to some percentage (determined by the resolutionThreshold) less resolute then the cellSize.
   * If the cellSize is more resolute than that threshold's allowance, this will return the next zoom level up.
   */
  def zoom(x: Double, y: Double, cellSize: CellSize): Int = {
    val ll1 = Point(x + cellSize.width, y + cellSize.height).reproject(crs, LatLng)
    val ll2 = Point(x, y).reproject(crs, LatLng)
    // Try UTM zone, if not, use Haversine distance formula
    val dist: Double =
      if(UTM.inValidZone(ll1.y)) {
        val utmCrs = UTM.getZoneCrs(ll1.x, ll1.y)
        val (p1, p2) = (ll1.reproject(LatLng, utmCrs), ll2.reproject(LatLng, utmCrs))

        math.max(math.abs(p1.x - p2.x), math.abs(p1.y - p2.y))
      } else {
        Haversine(ll1.x, ll1.y, ll2.x, ll2.y)
      }
    val z = (math.log(EARTH_CIRCUMFERENCE / (dist * tileSize)) / math.log(2)).toInt
    val zRes = EARTH_CIRCUMFERENCE / (math.pow(2, z) * tileSize)
    val nextZRes = EARTH_CIRCUMFERENCE / (math.pow(2, z + 1) * tileSize)
    val delta = zRes - nextZRes
    val diff = zRes - dist

    val zoom =
      if(diff / delta > resolutionThreshold) {
        z.toInt + 1
      } else {
        z.toInt
      }

    zoom
  }

  def levelFor(extent: Extent, cellSize: CellSize): LayoutLevel = {
    val worldExtent = Extent(-4152858, -3698040, 4235750, 4690568)
    val l =
      zoom(extent.xmin, extent.ymin, cellSize)

    levelForZoom(worldExtent, l)
  }

  def levelForZoom(id: Int): LayoutLevel =
    levelForZoom(Extent(-4152858, -3698040, 4235750, 4690568), id)

  def levelForZoom(worldExtent: Extent, id: Int): LayoutLevel = {
    if(id < 0)
      sys.error("TMS Tiling scheme does not have levels below 0")
    LayoutLevel(id, LayoutDefinition(worldExtent, TileLayout(layoutColsForZoom(id), layoutRowsForZoom(id), tileSize, tileSize)))
  }

  def zoomOut(level: LayoutLevel) = {
    val layout = level.layout
    val newZoom = level.zoom - 1
    val newSize = math.pow(2, newZoom).toInt
    new LayoutLevel(
      zoom = newZoom,
      layout = LayoutDefinition(
        extent = layout.extent,
        tileLayout = TileLayout(
          newSize,
          newSize,
          layout.tileCols,
          layout.tileRows
        )
      )
    )
  }

  def zoomIn(level: LayoutLevel) = {
    val layout = level.layout
    val newZoom = level.zoom + 1
    val newSize = math.pow(2, newZoom).toInt
    new LayoutLevel(
      zoom = newZoom,
      layout = LayoutDefinition(
        extent = layout.extent,
        tileLayout = TileLayout(
          newSize,
          newSize,
          layout.tileCols,
          layout.tileRows
        )
      )
    )
  }
}
