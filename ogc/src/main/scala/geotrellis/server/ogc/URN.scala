package geotrellis.server.ogc

import geotrellis.proj4.CRS

object URN {
  def fromCrs(crs: CRS): Option[String] = crs.epsgCode.map { code =>
    s"urn:ogc:def:crs:OGC:1.3:EPSG:$code"
  }

  def unsafeFromCrs(crs: CRS): String = {
    val code = crs.epsgCode.get
    s"urn:ogc:def:crs:OGC:1.3:EPSG:$code"
  }
}
