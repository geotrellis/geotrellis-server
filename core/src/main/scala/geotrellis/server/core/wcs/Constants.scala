package geotrellis.server.core.wcs


object Constants {
  // Map of incoming format string to normalized format string for supported formats.
  val SUPPORTED_FORMATS =
    Map(
      "geotiff" -> "geotiff",
      "geotif" -> "geotiff"
    )
}
