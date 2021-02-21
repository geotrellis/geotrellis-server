/*
 * Copyright 2020 Azavea
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package geotrellis.server.ogc.wcs

import geotrellis.server.ogc.ows.OwsDataRecord
import geotrellis.server.ogc.URN
import geotrellis.proj4.LatLng
import geotrellis.raster.reproject.ReprojectRasterExtent

import cats.Functor
import cats.syntax.functor._
import cats.syntax.option._
import opengis.ows._
import opengis.wcs._
import opengis._
import scalaxb._

import scala.xml.Elem
import java.net.{URI, URL}
import geotrellis.proj4.CRS

class CapabilitiesView[F[_]: Functor](wcsModel: WcsModel[F], serviceUrl: URL, extendedParameters: List[DomainType] = Nil) {
  def toXML: F[Elem] = {
    val serviceIdentification = ServiceIdentification(
      Title = LanguageStringType(wcsModel.serviceMetadata.identification.title) :: Nil,
      Abstract = LanguageStringType(
          wcsModel.serviceMetadata.identification.description
        ) :: Nil,
      Keywords = KeywordsType(
          wcsModel.serviceMetadata.identification.keywords
            .map(LanguageStringType(_)),
          None
        ) :: Nil,
      ServiceType = CodeType("OGC WCS"),
      ServiceTypeVersion = "1.1.1" :: Nil,
      Profile = Nil,
      Fees = wcsModel.serviceMetadata.identification.fees.getOrElse("NONE").some,
      AccessConstraints = "NONE" :: Nil
    )

    val contact = wcsModel.serviceMetadata.provider.contact
      .map { contact =>
        ResponsiblePartySubsetType(
          IndividualName = contact.name,
          PositionName = contact.position,
          ContactInfo = None,
          Role = contact.role.map(CodeType(_, Map()))
        )
      }
      .getOrElse(ResponsiblePartySubsetType())

    val serviceProvider =
      ServiceProvider(
        ProviderName = wcsModel.serviceMetadata.provider.name,
        ServiceContact = contact
      )

    val operationsMetadata = {
      val getCapabilities = Operation(
        DCP = DCP(
            OwsDataRecord(
              HTTP(
                OwsDataRecord(
                  "Get",
                  RequestMethodType(
                    attributes = Map(
                      "@{http://www.w3.org/1999/xlink}href" -> DataRecord(
                        serviceUrl.toURI
                      )
                    )
                  )
                ) :: Nil
              )
            )
          ) :: Nil,
        Parameter = DomainType(
            possibleValuesOption1 = OwsDataRecord(
              AllowedValues(
                OwsDataRecord(
                  ValueType("WCS")
                ) :: Nil
              )
            ),
            attributes = Map("@name" -> DataRecord("service"))
          ) :: DomainType(
            possibleValuesOption1 = OwsDataRecord(
              AllowedValues(
                OwsDataRecord(
                  ValueType("1.1.1")
                ) :: Nil
              )
            ),
            attributes = Map("@name" -> DataRecord("AcceptVersions"))
          ) :: Nil,
        attributes = Map("@name" -> DataRecord("GetCapabilities"))
      )

      val describeCoverage = Operation(
        DCP = DCP(
            OwsDataRecord(
              HTTP(
                OwsDataRecord(
                  "Get",
                  RequestMethodType(
                    attributes = Map(
                      "@{http://www.w3.org/1999/xlink}href" -> DataRecord(
                        serviceUrl.toURI
                      )
                    )
                  )
                ) :: Nil
              )
            )
          ) :: Nil,
        Parameter = DomainType(
            possibleValuesOption1 = OwsDataRecord(
              AllowedValues(
                OwsDataRecord(
                  ValueType("WCS")
                ) :: Nil
              )
            ),
            attributes = Map("@name" -> DataRecord("service"))
          ) :: DomainType(
            possibleValuesOption1 = OwsDataRecord(
              AllowedValues(
                OwsDataRecord(
                  ValueType("1.1.1")
                ) :: Nil
              )
            ),
            attributes = Map("@name" -> DataRecord("AcceptVersions"))
          ) :: Nil,
        attributes = Map("@name" -> DataRecord("DescribeCoverage"))
      )

      val getCoverage = Operation(
        DCP = DCP(
            OwsDataRecord(
              HTTP(
                OwsDataRecord(
                  "Get",
                  RequestMethodType(
                    attributes = Map(
                      "@{http://www.w3.org/1999/xlink}href" -> DataRecord(
                        serviceUrl.toURI
                      )
                    )
                  )
                ) :: Nil
              )
            )
          ) :: Nil,
        Parameter = (DomainType(
            possibleValuesOption1 = OwsDataRecord(
              AllowedValues(
                OwsDataRecord(
                  ValueType("WCS")
                ) :: Nil
              )
            ),
            attributes = Map("@name" -> DataRecord("service"))
          ) :: DomainType(
            possibleValuesOption1 = OwsDataRecord(
              AllowedValues(
                OwsDataRecord(
                  ValueType("1.1.1")
                ) :: Nil
              )
            ),
            attributes = Map("@name" -> DataRecord("AcceptVersions"))
          ) :: DomainType(
            possibleValuesOption1 = OwsDataRecord(
              AllowedValues(
                OwsDataRecord(
                  ValueType("nearest neighbor")
                ) :: OwsDataRecord(
                  ValueType("bilinear")
                ) :: OwsDataRecord(
                  ValueType("bicubic")
                ) :: Nil
              )
            ),
            DefaultValue = ValueType("nearest neighbor").some,
            attributes = Map("@name" -> DataRecord("InterpolationType"))
          ) :: Nil) ::: extendedParameters,
        attributes = Map("@name" -> DataRecord("GetCoverage"))
      )

      OperationsMetadata(
        Operation = getCapabilities :: describeCoverage :: getCoverage :: Nil
      )
    }

    CapabilitiesView.coverageSummaries(wcsModel).map { summaries =>
      val contents = Contents(CoverageSummary = summaries)
      scalaxb
        .toXML[Capabilities](
          obj = Capabilities(
            ServiceIdentification = serviceIdentification.some,
            ServiceProvider = serviceProvider.some,
            OperationsMetadata = operationsMetadata.some,
            Contents = contents.some,
            attributes = Map("@version" -> DataRecord("1.1.1"))
          ),
          namespace = None,
          elementLabel = "Capabilities".some,
          scope = wcsScope,
          typeAttribute = false
        )
        .asInstanceOf[Elem]
    }

  }
}

object CapabilitiesView {
  def coverageSummaries[F[_]: Functor](wcsModel: WcsModel[F]): F[List[CoverageSummaryType]] =
    wcsModel.sources.store.map(_.map { src =>
      val crs         = src.nativeCrs.head
      val wgs84extent = ReprojectRasterExtent(src.nativeRE, crs, LatLng).extent

      val uniqueCrs: List[CRS] = (
        crs :: LatLng :: wcsModel.supportedProjections
      ).distinct

      CoverageSummaryType(
        Title = LanguageStringType(src.title) :: Nil,
        Abstract = Nil,
        Keywords = Nil,
        WGS84BoundingBox = WGS84BoundingBoxType(
            LowerCorner = wgs84extent.ymin :: wgs84extent.xmin :: Nil,
            UpperCorner = wgs84extent.ymax :: wgs84extent.xmax :: Nil
          ) :: Nil,
        SupportedCRS =
          new URI("urn:ogc:def:crs:OGC::imageCRS") :: (uniqueCrs flatMap { crs => (URN.fromCrs(crs) map { new URI(_) }) }),
        SupportedFormat = "image/geotiff" :: "image/jpeg" :: "image/png" :: Nil,
        coveragesummarytypeoption = DataRecord(None, "Identifier".some, src.name)
      )
    }.toList)
}
