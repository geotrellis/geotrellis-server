package geotrellis.server.ogc

import scala.util.Try

sealed trait OutputFormat

object OutputFormat {
  case object GeoTiff extends OutputFormat
  case object Png extends OutputFormat
  case object Jpg extends OutputFormat

  def fromStringUnsafe(str: String) = str match {
    case "geotiff" | "geotif" | "image/geotiff" =>
      OutputFormat.GeoTiff
    case "image/png" =>
      OutputFormat.Png
    case "image/jpeg" =>
      OutputFormat.Jpg
  }

  def fromString(str: String) = Try(fromStringUnsafe(str)).toOption
}

