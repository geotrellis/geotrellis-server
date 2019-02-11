package geotrellis.server.ogc.wms

object Formats extends Enumeration {
  val GeoTiff, Png, Jpg = Value
}

object Constants {
  // Map of incoming format string to normalized format string for supported formats.
  val SUPPORTED_FORMATS =
    Map(
      "geotiff" -> Formats.GeoTiff,
      "geotif" -> Formats.GeoTiff,
      "image/png" -> Formats.Png,
      "image/geotiff" -> Formats.GeoTiff,
      "image/jpeg" -> Formats.Jpg
    )
}
