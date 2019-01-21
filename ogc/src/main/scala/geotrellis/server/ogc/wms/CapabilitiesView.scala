package geotrellis.server.ogc.wms

import geotrellis.proj4.{CRS, LatLng}
import java.net.URI

import scala.xml.{Elem, NodeSeq}

class CapabilitiesView(model: RasterSourcesModel, authority: String, port: Int, crs: CRS = LatLng) {
  def toXML: Elem = {
    import opengis.wms._

    val service = Service(
      Name = Name.fromString("WMS", opengis.wms.defaultScope),
      Title = "GeoTrellis WMS",
      OnlineResource = OnlineResource(),
      KeywordList = Some(KeywordList(Keyword("WMS") :: Keyword("GeoTrellis") :: Nil))
    )

    val capability = {
      val getCapabilities = OperationType(
        Format = List("text/xml"),
        DCPType = List(DCPType(
          HTTP(Get = Get(OnlineResource(Map(
            "@{http://www.w3.org/1999/xlink}href" -> scalaxb.DataRecord(new URI(s"http://${authority}:${port}/wms")),
            "@{http://www.w3.org/1999/xlink}type" -> scalaxb.DataRecord(xlink.Simple: xlink.TypeType)))))
        )))

      val getMap = OperationType(
        Format = List("text/xml", "image/png", "image/geotiff", "image/jpeg"),
        DCPType = List(DCPType(
          HTTP(Get = Get(OnlineResource(Map(
            "@{http://www.w3.org/1999/xlink}href" -> scalaxb.DataRecord(new URI(s"http://${authority}:${port}/wms")),
            "@{http://www.w3.org/1999/xlink}type" -> scalaxb.DataRecord(xlink.Simple: xlink.TypeType)))))
        )))

      Capability(
        Request = Request(GetCapabilities = getCapabilities, GetMap = getMap, GetFeatureInfo = None),
        Exception = Exception(List("XML", "INIMAGE", "BLANK")),
        Layer = Some(model.toLayer(crs))
      )
    }

    /**
      * Default scope generates an incorrect XML file (in the incorrect scope, prefixes all XML elements with `wms:` prefix.
      *
      * val defaultScope = scalaxb.toScope(Some("ogc") -> "http://www.opengis.net/ogc",
      * Some("wms") -> "http://www.opengis.net/wms",
      * Some("xlink") -> "http://www.w3.org/1999/xlink",
      * Some("xs") -> "http://www.w3.org/2001/XMLSchema",
      * Some("xsi") -> "http://www.w3.org/2001/XMLSchema-instance")
      */

    val ret: NodeSeq = scalaxb.toXML[opengis.wms.WMS_Capabilities](
      obj = WMS_Capabilities(service, capability, Map("@version" -> scalaxb.DataRecord("1.3.0"))),
      namespace = None,
      elementLabel = Some("WMS_Capabilities"),
      scope = scalaxb.toScope(
        Some("ogc") -> "http://www.opengis.net/ogc",
        Some("xlink") -> "http://www.w3.org/1999/xlink",
        Some("xs") -> "http://www.w3.org/2001/XMLSchema",
        Some("xsi") -> "http://www.w3.org/2001/XMLSchema-instance"
      ),
      typeAttribute = false
    )

    ret.asInstanceOf[scala.xml.Elem]
  }
}
