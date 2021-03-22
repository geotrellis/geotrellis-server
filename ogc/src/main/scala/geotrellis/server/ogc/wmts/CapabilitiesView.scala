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

package geotrellis.server.ogc.wmts

import geotrellis.server.ogc.ows.OwsDataRecord
import geotrellis.server.ogc._
import geotrellis.vector.Extent
import geotrellis.raster.reproject._
import geotrellis.proj4.LatLng

import cats.Monad
import cats.syntax.functor._
import cats.syntax.option._
import opengis.ows._
import opengis.wmts._
import opengis._
import scalaxb._

import java.net.{URI, URL}
import scala.xml.Elem

/** @param wmtsModel WmtsModel of layers and tile matrices we can report
  * @param serviceUrl URL where this service can be reached with addition of `?request=` query parameter
  */
class CapabilitiesView[F[_]: Monad](wmtsModel: WmtsModel[F], serviceUrl: URL) {
  import CapabilitiesView._

  def toXML: F[Elem] = {
    val serviceIdentification =
      ServiceIdentification(
        Title = LanguageStringType(
          wmtsModel.serviceMetadata.identification.title
        ) :: Nil,
        Abstract = LanguageStringType(
          wmtsModel.serviceMetadata.identification.description
        ) :: Nil,
        Keywords = KeywordsType(
          wmtsModel.serviceMetadata.identification.keywords.map(LanguageStringType(_)),
          None
        ) :: Nil,
        ServiceType = CodeType("OGC WMTS"),
        ServiceTypeVersion = "1.0.0" :: Nil
      )

    val contact = wmtsModel.serviceMetadata.provider.contact
      .map { contact =>
        ResponsiblePartySubsetType(
          IndividualName = contact.name,
          PositionName = contact.position,
          ContactInfo = None,
          Role = contact.role.map(CodeType(_, Map()))
        )
      }
      .getOrElse(ResponsiblePartySubsetType())

    val serviceProvider = ServiceProvider(ProviderName = wmtsModel.serviceMetadata.provider.name, ServiceContact = contact)

    val operationsMetadata = {
      val getCapabilities = Operation(
        DCP = DCP(
          OwsDataRecord(
            HTTP(
              OwsDataRecord(
                "Get",
                RequestMethodType(
                  Constraint = DomainType(
                    possibleValuesOption1 = OwsDataRecord(
                      AllowedValues(
                        OwsDataRecord(
                          ValueType("KVP")
                        ) :: Nil
                      )
                    ),
                    attributes = Map("@name" -> DataRecord("GetEncoding"))
                  ) :: Nil,
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
        attributes = Map("@name" -> DataRecord("GetCapabilities"))
      )

      val getTile = Operation(
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
        attributes = Map("@name" -> DataRecord("GetTile"))
      )

      val getFeatureInfo = Operation(
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
        attributes = Map("@name" -> DataRecord("GetFeatureInfo"))
      )

      OperationsMetadata(Operation = getCapabilities :: getTile :: getFeatureInfo :: Nil)
    }

    val layers         = modelAsLayers(wmtsModel)
    val tileMatrixSets = wmtsModel.matrices.map(_.toXml)

    // that's how layers metadata is generated
    layers.map { layers =>
      val contents = ContentsType(
        DatasetDescriptionSummary = layers,
        TileMatrixSet = tileMatrixSets
      )

      scalaxb
        .toXML[opengis.wmts.Capabilities](
          obj = Capabilities(
            ServiceIdentification = serviceIdentification.some,
            ServiceProvider = serviceProvider.some,
            OperationsMetadata = operationsMetadata.some,
            Contents = contents.some,
            attributes = Map("@version" -> DataRecord("1.0.0"))
          ),
          namespace = None,
          elementLabel = "Capabilities".some,
          scope = wmtsScope,
          typeAttribute = false
        )
        .asInstanceOf[scala.xml.Elem]
    }
  }
}

object CapabilitiesView {
  implicit def toRecord[T: CanWriteXML](t: T): DataRecord[T] = DataRecord(t)

  def boundingBox(extent: Extent): WGS84BoundingBoxType =
    WGS84BoundingBoxType(
      LowerCorner = extent.xmin :: extent.ymin :: Nil,
      UpperCorner = extent.xmax :: extent.ymax :: Nil,
      attributes = Map("@crs" -> DataRecord(new URI("urn:ogc:def:crs:OGC:2:84")))
    )

  implicit class OgcSourceMethods(val self: OgcSource) {
    def toLayerType(tileMatrixSet: List[GeotrellisTileMatrixSet]): LayerType = {
      val wgs84extent: Extent                      = ReprojectRasterExtent(self.nativeRE, self.nativeCrs.head, LatLng).extent
      val tileMatrixLimits: List[TileMatrixLimits] = tileMatrixSet.flatMap { tms =>
        tms.tileMatrix.map { tm =>
          val gridBounds = tm.layout.mapTransform(ReprojectRasterExtent(self.nativeRE, self.nativeCrs.head, tms.supportedCrs).extent)
          TileMatrixLimits(
            TileMatrix = tm.identifier,
            MinTileRow = gridBounds.rowMin,
            MaxTileRow = gridBounds.rowMax,
            MinTileCol = gridBounds.colMin,
            MaxTileCol = gridBounds.colMax
          )
        }
      }

      LayerType(
        Title = LanguageStringType(self.title) :: Nil,
        Abstract = Nil,
        Keywords = Nil,
        WGS84BoundingBox = boundingBox(wgs84extent) :: Nil,
        Identifier = CodeType(self.name),
        BoundingBox = Nil,
        Metadata = Nil,
        DatasetDescriptionSummary = Nil,
        Style = self.styles.map { style =>
          Style(
            Title = LanguageStringType(style.title) :: Nil,
            Abstract = LanguageStringType(style.title) :: Nil,
            Identifier = CodeType(style.name),
            Keywords = Nil,
            LegendURL = Nil
          )
        },
        Format = "image/png" :: "image/jpeg" :: Nil,
        InfoFormat = "text/xml" :: Nil,
        Dimension = Nil,
        // NOTE: This "ID" MUST correspond to the TileMatrixSet ID for the layers to show up in QGIS
        TileMatrixSetLink = TileMatrixSetLink(
          TileMatrixSet = "GoogleMapsCompatible",
          TileMatrixSetLimits = TileMatrixSetLimits(tileMatrixLimits).some
        )
          :: Nil,
        ResourceURL = Nil
      )
    }
  }

  def modelAsLayers[F[_]: Monad](wmtsModel: WmtsModel[F]): F[List[DataRecord[LayerType]]] =
    wmtsModel.sources.store
      .map { sources =>
        sources map { src =>
          DataRecord(
            "wms".some,
            "Layer".some,
            src.toLayerType(wmtsModel.matrices)
          )

        }
      }
}
