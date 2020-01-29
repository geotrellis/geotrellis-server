package geotrellis.server.ogc.wcs

import geotrellis.server.ogc.ows.ResponsiblePartySubset
import geotrellis.server.ogc.URN
import geotrellis.proj4.LatLng
import geotrellis.raster.reproject.ReprojectRasterExtent

import opengis.ows._
import opengis.wcs._
import opengis._
import scalaxb._

import scala.xml.{Elem, NodeSeq}
import java.net.{URI, URL}

class CapabilitiesView(
  wcsModel: WcsModel,
  serviceUrl: URL
) {
  import CapabilitiesView._

  def toXML: Elem = {
    val serviceIdentification = ServiceIdentification(
      Title = LanguageStringType(wcsModel.serviceMetadata.identification.title) :: Nil,
      Abstract = LanguageStringType(wcsModel.serviceMetadata.identification.description) :: Nil,
      Keywords = KeywordsType(wcsModel.serviceMetadata.identification.keywords.map(LanguageStringType(_)), None) :: Nil,
      ServiceType = CodeType("OGC WCS"),
      ServiceTypeVersion = "1.1.1" :: Nil,
      Profile = Nil,
      Fees = Some(wcsModel.serviceMetadata.identification.fees.getOrElse("NONE")),
      AccessConstraints = "NONE" :: Nil
    )

    val contact = wcsModel.serviceMetadata.provider.contact.map({ contact: ResponsiblePartySubset =>
      ResponsiblePartySubsetType(
        IndividualName = contact.name,
        PositionName = contact.position,
        ContactInfo = None,
        Role = contact.role.map(CodeType(_, Map()))
      )
    }).getOrElse(ResponsiblePartySubsetType())

    val serviceProvider =
      ServiceProvider(
        ProviderName = wcsModel.serviceMetadata.provider.name,
        ServiceContact = contact
      )

    val operationsMetadata = {
      val getCapabilities = Operation(
        DCP = DCP(
          scalaxb.DataRecord(
            Some("ows"),
            Some("ows:HTTP"),
            HTTP(scalaxb.DataRecord(
              Some("ows"),
              Some("ows:Get"),
              RequestMethodType(
                attributes = Map("@{http://www.w3.org/1999/xlink}href" -> scalaxb.DataRecord(serviceUrl.toURI))
              )) :: Nil)
          )
        ) :: Nil,
        Parameter = DomainType(
          possibleValuesOption1 = scalaxb.DataRecord(
            Some("ows"),
            Some("ows:AllowedValues"),
            AllowedValues(
              scalaxb.DataRecord(
                Some("ows"),
                Some("ows:Value"),
                ValueType("WCS")
              ) :: Nil)
          ),
          attributes = Map("@name" -> scalaxb.DataRecord("service"))
        ) :: DomainType(
          possibleValuesOption1 = scalaxb.DataRecord(
            Some("ows"),
            Some("ows:AllowedValues"),
            AllowedValues(
              scalaxb.DataRecord(
                Some("ows"),
                Some("ows:Value"),
                ValueType("1.1.1")
              ) :: Nil)
          ),
          attributes = Map("@name" -> scalaxb.DataRecord("AcceptVersions"))
        ) :: Nil,
        attributes = Map("@name" -> scalaxb.DataRecord("GetCapabilities"))
      )

      val describeCoverage = Operation(
        DCP = DCP(
          scalaxb.DataRecord(
            Some("ows"),
            Some("ows:HTTP"),
            HTTP(scalaxb.DataRecord(
              Some("ows"),
              Some("ows:Get"),
              RequestMethodType(
                attributes = Map("@{http://www.w3.org/1999/xlink}href" -> scalaxb.DataRecord(serviceUrl.toURI))
              )) :: Nil)
          )
        ) :: Nil,
        Parameter = DomainType(
          possibleValuesOption1 = scalaxb.DataRecord(
            Some("ows"),
            Some("ows:AllowedValues"),
            AllowedValues(
              scalaxb.DataRecord(
                Some("ows"),
                Some("ows:Value"),
                ValueType("WCS")
              ) :: Nil)
          ),
          attributes = Map("@name" -> scalaxb.DataRecord("service"))
        ) :: DomainType(
          possibleValuesOption1 = scalaxb.DataRecord(
            Some("ows"),
            Some("ows:AllowedValues"),
            AllowedValues(
              scalaxb.DataRecord(
                Some("ows"),
                Some("ows:Value"),
                ValueType("1.1.1")
              ) :: Nil)
          ),
          attributes = Map("@name" -> scalaxb.DataRecord("AcceptVersions"))
        ) :: Nil,
        attributes = Map("@name" -> scalaxb.DataRecord("DescribeCoverage"))
      )

      val getCoverage = Operation(
        DCP = DCP(
          scalaxb.DataRecord(
            Some("ows"),
            Some("ows:HTTP"),
            HTTP(scalaxb.DataRecord(
              Some("ows"),
              Some("ows:Get"),
              RequestMethodType(
                attributes = Map("@{http://www.w3.org/1999/xlink}href" -> scalaxb.DataRecord(serviceUrl.toURI))
              )) :: Nil)
          )
        ) :: Nil,
        Parameter = DomainType(
          possibleValuesOption1 = scalaxb.DataRecord(
            Some("ows"),
            Some("ows:AllowedValues"),
            AllowedValues(
              scalaxb.DataRecord(
                Some("ows"),
                Some("ows:Value"),
                ValueType("WCS")
              ) :: Nil)
          ),
          attributes = Map("@name" -> scalaxb.DataRecord("service"))
        ) :: DomainType(
          possibleValuesOption1 = scalaxb.DataRecord(
            Some("ows"),
            Some("ows:AllowedValues"),
            AllowedValues(
              scalaxb.DataRecord(
                Some("ows"),
                Some("ows:Value"),
                ValueType("1.1.1")
              ) :: Nil)
          ),
          attributes = Map("@name" -> scalaxb.DataRecord("AcceptVersions"))
        ) :: DomainType(
          possibleValuesOption1 = scalaxb.DataRecord(
            Some("ows"),
            Some("ows:AllowedValues"),
            AllowedValues(
              scalaxb.DataRecord(
                Some("ows"),
                Some("ows:Value"),
                ValueType("nearest neighbor")
              ) :: scalaxb.DataRecord(
                Some("ows"),
                Some("ows:Value"),
                ValueType("bilinear")
              ) :: scalaxb.DataRecord(
                Some("ows"),
                Some("ows:Value"),
                ValueType("bicubic")
              ) :: Nil)
          ),
          DefaultValue = Some(ValueType("nearest neighbor")),
          attributes = Map("@name" -> scalaxb.DataRecord("InterpolationType"))
        ) :: Nil,
        attributes = Map("@name" -> scalaxb.DataRecord("GetCoverage"))
      )

      OperationsMetadata(
        Operation = getCapabilities :: describeCoverage :: getCoverage :: Nil
      )
    }

    val contents = {
      Contents(
        CoverageSummary = addLayers(wcsModel)
      )
    }

    val ret: NodeSeq = scalaxb.toXML[opengis.wcs.Capabilities](
      obj = Capabilities(
        ServiceIdentification = Some(serviceIdentification),
        ServiceProvider       = Some(serviceProvider),
        OperationsMetadata    = Some(operationsMetadata),
        Contents              = Some(contents),
        attributes            = Map("@version" -> scalaxb.DataRecord("1.1.1"))
      ),
      namespace = None,
      elementLabel = Some("Capabilities"),
      scope = constrainedWCSScope,
      typeAttribute = false
    )

    ret.asInstanceOf[scala.xml.Elem]
  }
}

object CapabilitiesView {
  implicit def toRecord[T: CanWriteXML](t: T): scalaxb.DataRecord[T] = scalaxb.DataRecord(t)

  def addLayers(wcsModel: WcsModel): List[CoverageSummaryType] =
    wcsModel.sourceLookup.map { case (identifier, src) =>
      val crs = src.nativeCrs.head
      val wgs84extent = ReprojectRasterExtent(src.nativeRE, crs, LatLng).extent

      CoverageSummaryType(
        Title    = LanguageStringType(src.title) :: Nil,
        Abstract = Nil,
        Keywords = Nil,
        WGS84BoundingBox = WGS84BoundingBoxType(
          LowerCorner = wgs84extent.xmin :: wgs84extent.ymin :: Nil,
          UpperCorner = wgs84extent.xmax :: wgs84extent.ymax :: Nil
        ) :: Nil,
        SupportedCRS = new URI(URN.unsafeFromCrs(crs)) :: Nil,
        SupportedFormat = "image/GeoTIFF" :: "image/JPEG" :: "image/PNG" :: Nil,
        coveragesummarytypeoption = scalaxb.DataRecord(None, Some("Identifier"), identifier)
      )
    }.toList
}
