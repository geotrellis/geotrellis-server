package geotrellis.server.ogc

import geotrellis.proj4.CRS

object URN {
  def fromCrs(crs: CRS): Option[String] = crs.epsgCode.map { code =>
    s"urn:ogc:def:crs:EPSG:9.2:$code"
  }

  def unsafeFromCrs(crs: CRS): String = {
    fromCrs(crs)
      .getOrElse(throw new Exception(s"Unrecognized CRS: $crs. Unable to construct URN"))
  }
}
