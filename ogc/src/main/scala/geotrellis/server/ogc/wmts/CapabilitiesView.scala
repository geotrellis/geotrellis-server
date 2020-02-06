package geotrellis.server.ogc.wmts

import geotrellis.server.ogc.ows.OwsDataRecord
import geotrellis.server.ogc._
import geotrellis.vector.Extent
import geotrellis.raster.reproject._
import geotrellis.proj4.LatLng

import cats.syntax.option._
import opengis.ows._
import opengis.wmts._
import opengis._
import scalaxb._

import java.net.{URI, URL}
import scala.xml.Elem

/**
  *
  * @param wmtsModel WmtsModel of layers and tile matrices we can report
  * @param serviceUrl URL where this service can be reached with addition of `?request=` query parameter
  */
class CapabilitiesView(
  wmtsModel: WmtsModel,
  serviceUrl: URL
) {
  import CapabilitiesView._

  def toXML: Elem = {
    val serviceIdentification =
      ServiceIdentification(
        Title              = LanguageStringType(wmtsModel.serviceMetadata.identification.title) :: Nil,
        Abstract           = LanguageStringType(wmtsModel.serviceMetadata.identification.description) :: Nil,
        Keywords           = KeywordsType(wmtsModel.serviceMetadata.identification.keywords.map(LanguageStringType(_)), None) :: Nil,
        ServiceType        = CodeType("OGC WMTS"),
        ServiceTypeVersion = "1.0.0" :: Nil
      )

    val contact = wmtsModel.serviceMetadata.provider.contact.map { contact =>
      ResponsiblePartySubsetType(
        IndividualName = contact.name,
        PositionName   = contact.position,
        ContactInfo    = None,
        Role           = contact.role.map(CodeType(_, Map()))
      )
    }.getOrElse(ResponsiblePartySubsetType())

    val serviceProvider =
      ServiceProvider(
        ProviderName   = wmtsModel.serviceMetadata.provider.name,
        ServiceContact = contact
      )

    val operationsMetadata = {
      val getCapabilities = Operation(
        DCP = DCP(
          OwsDataRecord(
            HTTP(DataRecord(
              "ows".some,
              "ows:Get".some,
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
                attributes = Map("@{http://www.w3.org/1999/xlink}href" -> DataRecord(serviceUrl.toURI))
              )) :: Nil)
          )
        ) :: Nil,
        attributes = Map("@name" -> DataRecord("GetCapabilities"))
      )

      val getTile = Operation(
        DCP = DCP(
          OwsDataRecord(
            HTTP(DataRecord(
              "ows".some,
              "ows:Get".some,
              RequestMethodType(
                attributes = Map("@{http://www.w3.org/1999/xlink}href" -> DataRecord(serviceUrl.toURI))
              )) :: Nil)
          )
        ) :: Nil,
        attributes = Map("@name" -> DataRecord("GetTile"))
      )

      val getFeatureInfo = Operation(
        DCP = DCP(
          OwsDataRecord(
            HTTP(DataRecord(
              "ows".some,
              "ows:Get".some,
              RequestMethodType(
                attributes = Map("@{http://www.w3.org/1999/xlink}href" -> DataRecord(serviceUrl.toURI))
              )) :: Nil)
          )
        ) :: Nil,
        attributes = Map("@name" -> DataRecord("GetFeatureInfo"))
      )

      OperationsMetadata(
        Operation = getCapabilities :: getTile :: getFeatureInfo :: Nil
      )
    }

    val layers = modelAsLayers(wmtsModel)
    val tileMatrixSets = wmtsModel.matrices.map(_.toXml)

    // that's how layers metadata is generated
    val contents = ContentsType(
      DatasetDescriptionSummary = layers,
      TileMatrixSet = tileMatrixSets
    )

    scalaxb.toXML[opengis.wmts.Capabilities](
      obj = Capabilities(
        ServiceIdentification = serviceIdentification.some,
        ServiceProvider       = serviceProvider.some,
        OperationsMetadata    = operationsMetadata.some,
        Contents              = contents.some,
        attributes            = Map("@version" -> DataRecord("1.0.0"))
      ),
      namespace     = None,
      elementLabel  = "Capabilities".some,
      scope         = constrainedWMTSScope,
      typeAttribute = false
    ).asInstanceOf[scala.xml.Elem]
  }
}

object CapabilitiesView {
  implicit def toRecord[T: CanWriteXML](t: T): DataRecord[T] = DataRecord(t)

  def boundingBox(extent: Extent): WGS84BoundingBoxType =
    WGS84BoundingBoxType(
      LowerCorner = Seq(extent.xmin, extent.ymin),
      UpperCorner = Seq(extent.xmax, extent.ymax),
      attributes  = Map("@crs" -> DataRecord(new URI("urn:ogc:def:crs:OGC:2:84")))
    )

  implicit class OgcSourceMethods(val self: OgcSource) {
    def toLayerType(layerName: String, tileMatrixSet: List[GeotrellisTileMatrixSet]): LayerType = {
      val wgs84extent: Extent = ReprojectRasterExtent(self.nativeRE, self.nativeCrs.head, LatLng).extent
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
        Title = LanguageStringType(layerName) :: Nil,
        Abstract = Nil,
        Keywords = Nil,
        WGS84BoundingBox = List(boundingBox(wgs84extent)),
        Identifier = CodeType(layerName),
        BoundingBox = Nil,
        Metadata = Nil,
        DatasetDescriptionSummary = Nil,
        Style = List(Style(
          Title      = List(LanguageStringType("Style")),
          Abstract   = List(LanguageStringType("AbstractStyle")),
          Identifier = CodeType("StyleID"),
          Keywords   = Nil,
          LegendURL  = Nil
        )),
        Format = List("image/png", "image/jpeg"),
        InfoFormat = List("text/xml"),
        Dimension = Nil,
        // NOTE: This "ID" MUST correspond to the TileMatrixSet ID for the layers to show up in QGIS
        TileMatrixSetLink = List(
          TileMatrixSetLink(
            TileMatrixSet       = "GoogleMapsCompatible",
            TileMatrixSetLimits = TileMatrixSetLimits(tileMatrixLimits).some
          )
        ),
        ResourceURL = Nil
      )
    }
  }

  def modelAsLayers(wmtsModel: WmtsModel): List[DataRecord[LayerType]] =
    wmtsModel
      .sourceLookup
      .map { case (key, value) => DataRecord("wms".some, "Layer".some, value.toLayerType(key, wmtsModel.matrices)) }
      .toList
}
