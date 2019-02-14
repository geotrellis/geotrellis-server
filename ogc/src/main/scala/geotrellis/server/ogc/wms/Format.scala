package geotrellis.server.ogc.wms

sealed trait Format

object Format {
  case object GeoTiff extends Format
  case object Png extends Format
  case object Jpg extends Format
}

