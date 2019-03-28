package geotrellis.server.ogc

import scala.util.Try

/** The output formats supported across currently implemented OGC services */
sealed trait OutputFormat

object OutputFormat {
  case object GeoTiff extends OutputFormat { override def toString = "image/geotiff" }
  case object Png extends OutputFormat { override def toString = "image/png" }
  case object Jpg extends OutputFormat { override def toString = "image/jpg" }

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

