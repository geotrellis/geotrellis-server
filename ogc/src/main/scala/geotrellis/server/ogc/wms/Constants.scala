package geotrellis.server.ogc.wms

object Constants {
  // Map of incoming format string to normalized format string for supported formats.
  val SUPPORTED_FORMATS =
    Map(
      "geotiff" -> Format.GeoTiff,
      "geotif" -> Format.GeoTiff,
      "image/png" -> Format.Png,
      "image/geotiff" -> Format.GeoTiff,
      "image/jpeg" -> Format.Jpg
    )
}
