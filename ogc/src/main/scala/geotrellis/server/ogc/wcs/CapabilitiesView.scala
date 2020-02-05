package geotrellis.server.ogc.wcs

import geotrellis.server.ogc.ows.ResponsiblePartySubset
import geotrellis.server.ogc.URN
import geotrellis.proj4.LatLng
import geotrellis.raster.reproject.ReprojectRasterExtent

import cats.syntax.option._
import opengis.ows._
import opengis.wcs._
import opengis._
import scalaxb._

import scala.xml.Elem
import java.net.{URI, URL}

class CapabilitiesView(
  wcsModel: WcsModel,
  serviceUrl: URL
) {
  def toXML: Elem = {
    val serviceIdentification = ServiceIdentification(
      Title              = LanguageStringType(wcsModel.serviceMetadata.identification.title) :: Nil,
      Abstract           = LanguageStringType(wcsModel.serviceMetadata.identification.description) :: Nil,
      Keywords           = KeywordsType(wcsModel.serviceMetadata.identification.keywords.map(LanguageStringType(_)), None) :: Nil,
      ServiceType        = CodeType("OGC WCS"),
      ServiceTypeVersion = "1.1.1" :: Nil,
      Profile            = Nil,
      Fees               = wcsModel.serviceMetadata.identification.fees.getOrElse("NONE").some,
      AccessConstraints  = "NONE" :: Nil
    )

    val contact = wcsModel.serviceMetadata.provider.contact.map { contact: ResponsiblePartySubset =>
      ResponsiblePartySubsetType(
        IndividualName = contact.name,
        PositionName   = contact.position,
        ContactInfo    = None,
        Role           = contact.role.map(CodeType(_, Map()))
      )
    }.getOrElse(ResponsiblePartySubsetType())

    val serviceProvider =
      ServiceProvider(
        ProviderName   = wcsModel.serviceMetadata.provider.name,
        ServiceContact = contact
      )

    val operationsMetadata = {
      val getCapabilities = Operation(
        DCP = DCP(
          DataRecord(
            "ows".some,
            "ows:HTTP".some,
            HTTP(DataRecord(
              "ows".some,
              "ows:Get".some,
              RequestMethodType(
                attributes = Map("@{http://www.w3.org/1999/xlink}href" -> DataRecord(serviceUrl.toURI))
              )) :: Nil)
          )
        ) :: Nil,
        Parameter = DomainType(
          possibleValuesOption1 = DataRecord(
            "ows".some,
            "ows:AllowedValues".some,
            AllowedValues(
              DataRecord(
                "ows".some,
                "ows:Value".some,
                ValueType("WCS")
              ) :: Nil)
          ),
          attributes = Map("@name" -> DataRecord("service"))
        ) :: DomainType(
          possibleValuesOption1 = DataRecord(
            "ows".some,
            "ows:AllowedValues".some,
            AllowedValues(
              DataRecord(
                "ows".some,
                "ows:Value".some,
                ValueType("1.1.1")
              ) :: Nil)
          ),
          attributes = Map("@name" -> DataRecord("AcceptVersions"))
        ) :: Nil,
        attributes = Map("@name" -> DataRecord("GetCapabilities"))
      )

      val describeCoverage = Operation(
        DCP = DCP(
          DataRecord(
            "ows".some,
            "ows:HTTP".some,
            HTTP(DataRecord(
              "ows".some,
              "ows:Get".some,
              RequestMethodType(
                attributes = Map("@{http://www.w3.org/1999/xlink}href" -> DataRecord(serviceUrl.toURI))
              )) :: Nil)
          )
        ) :: Nil,
        Parameter = DomainType(
          possibleValuesOption1 = DataRecord(
            "ows".some,
            "ows:AllowedValues".some,
            AllowedValues(
              DataRecord(
                "ows".some,
                "ows:Value".some,
                ValueType("WCS")
              ) :: Nil)
          ),
          attributes = Map("@name" -> DataRecord("service"))
        ) :: DomainType(
          possibleValuesOption1 = DataRecord(
            "ows".some,
            "ows:AllowedValues".some,
            AllowedValues(
              DataRecord(
                "ows".some,
                "ows:Value".some,
                ValueType("1.1.1")
              ) :: Nil)
          ),
          attributes = Map("@name" -> DataRecord("AcceptVersions"))
        ) :: Nil,
        attributes = Map("@name" -> DataRecord("DescribeCoverage"))
      )

      val getCoverage = Operation(
        DCP = DCP(
          DataRecord(
            "ows".some,
            "ows:HTTP".some,
            HTTP(DataRecord(
              "ows".some,
              "ows:Get".some,
              RequestMethodType(
                attributes = Map("@{http://www.w3.org/1999/xlink}href" -> DataRecord(serviceUrl.toURI))
              )) :: Nil)
          )
        ) :: Nil,
        Parameter = DomainType(
          possibleValuesOption1 = DataRecord(
            "ows".some,
            "ows:AllowedValues".some,
            AllowedValues(
              DataRecord(
                "ows".some,
                "ows:Value".some,
                ValueType("WCS")
              ) :: Nil)
          ),
          attributes = Map("@name" -> DataRecord("service"))
        ) :: DomainType(
          possibleValuesOption1 = DataRecord(
            "ows".some,
            "ows:AllowedValues".some,
            AllowedValues(
              DataRecord(
                "ows".some,
                "ows:Value".some,
                ValueType("1.1.1")
              ) :: Nil)
          ),
          attributes = Map("@name" -> DataRecord("AcceptVersions"))
        ) :: DomainType(
          possibleValuesOption1 = DataRecord(
            "ows".some,
            "ows:AllowedValues".some,
            AllowedValues(
              DataRecord(
                "ows".some,
                "ows:Value".some,
                ValueType("nearest neighbor")
              ) :: DataRecord(
                "ows".some,
                "ows:Value".some,
                ValueType("bilinear")
              ) :: DataRecord(
                "ows".some,
                "ows:Value".some,
                ValueType("bicubic")
              ) :: Nil)
          ),
          DefaultValue = ValueType("nearest neighbor").some,
          attributes = Map("@name" -> DataRecord("InterpolationType"))
        ) :: Nil,
        attributes = Map("@name" -> DataRecord("GetCoverage"))
      )

      OperationsMetadata(Operation = getCapabilities :: describeCoverage :: getCoverage :: Nil)
    }

    val contents = Contents(CoverageSummary = CapabilitiesView.coverageSummaries(wcsModel))

    scalaxb.toXML[Capabilities](
      obj = Capabilities(
        ServiceIdentification = serviceIdentification.some,
        ServiceProvider       = serviceProvider.some,
        OperationsMetadata    = operationsMetadata.some,
        Contents              = contents.some,
        attributes            = Map("@version" -> DataRecord("1.1.1"))
      ),
      namespace     = None,
      elementLabel  = "Capabilities".some,
      scope         = constrainedWCSScope,
      typeAttribute = false
    ).asInstanceOf[Elem]
  }
}

object CapabilitiesView {
  def coverageSummaries(wcsModel: WcsModel): List[CoverageSummaryType] =
    wcsModel.sourceLookup.map { case (identifier, src) =>
      val crs = src.nativeCrs.head
      val wgs84extent = ReprojectRasterExtent(src.nativeRE, crs, LatLng).extent

      CoverageSummaryType(
        Title    = LanguageStringType(src.title) :: Nil,
        Abstract = Nil,
        Keywords = Nil,
        WGS84BoundingBox = WGS84BoundingBoxType(
          LowerCorner = wgs84extent.ymin :: wgs84extent.xmin :: Nil,
          UpperCorner = wgs84extent.ymax :: wgs84extent.xmax :: Nil
        ) :: Nil,
        SupportedCRS =
          new URI(URN.unsafeFromCrs(crs)) ::
          new URI(URN.unsafeFromCrs(LatLng)) ::
          new URI("urn:ogc:def:crs:OGC::imageCRS") :: Nil,
        SupportedFormat = "image/geotiff" :: "image/jpeg" :: "image/png" :: Nil,
        coveragesummarytypeoption = DataRecord(None, "Identifier".some, identifier)
      )
    }.toList
}
