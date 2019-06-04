package geotrellis.server.stac

import cats.implicits._
import io.circe._

sealed abstract class StacMediaType(val repr: String) {
  override def toString: String = repr
}

object StacMediaType {

  private def fromString(s: String): StacMediaType = s match {
    case "image/tiff" => `image/tiff`
    case "image/vnd.stac.geotiff" => `image/tiff`
    case "image/vnd.stac.geotiff; cloud-optimized=true" => `image/cog`
    case "image/jp2" => `image/jp2`
    case "image/png" => `image/png`
    case "image/jpeg" => `image/jpeg`
    case "text/xml" => `text/xml`
    case "application/json" => `application/json`
    case "text/plain" => `text/plain`
    case "application/geo+json" => `application/geo+json`
    case "application/geopackage+sqlite3" => `application/geopackage+sqlite3`
    case "application/x-hdf5" => `application/x-hdf5`
    case "application/x-hdf" => `application/x-hdf`
    case _ => throw new Exception(s"Cannot construct media type from string: $s")
  }

  implicit val encMediaType: Encoder[StacMediaType] =
    Encoder.encodeString.contramap[StacMediaType](_.toString)

  implicit val decMediaType: Decoder[StacMediaType] =
    Decoder.decodeString.emap { str =>
      Either.catchNonFatal(fromString(str)).leftMap(_ => "StacLinkType")
    }
}

case object `image/tiff` extends StacMediaType("image/tiff")
case object `image/vnd.stac.geotiff` extends StacMediaType("image/tiff")
case object `image/cog` extends StacMediaType("image/vnd.stac.geotiff; cloud-optimized=true")
case object `image/jp2` extends StacMediaType("image/jp2")
case object `image/png` extends StacMediaType("image/png")
case object `image/jpeg` extends StacMediaType("image/jpeg")
case object `text/xml` extends StacMediaType("text/xml")
case object `application/xml` extends StacMediaType("text/xml")
case object `application/json` extends StacMediaType("application/json")
case object `text/plain` extends StacMediaType("text/plain")
case object `application/geo+json` extends StacMediaType("application/geo+json")
case object `application/geopackage+sqlite3` extends StacMediaType("application/geopackage+sqlite3")
case object `application/x-hdf5` extends StacMediaType("application/x-hdf5")
case object `application/x-hdf` extends StacMediaType("application/x-hdf")
