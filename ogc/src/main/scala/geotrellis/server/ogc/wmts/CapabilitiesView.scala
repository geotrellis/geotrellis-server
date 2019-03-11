package geotrellis.server.ogc.wmts

import geotrellis.proj4.{CRS, WebMercator, LatLng}
import geotrellis.contrib.vlm.RasterSource
import geotrellis.server.ogc._
import geotrellis.vector.Extent
import geotrellis.raster.reproject._

import opengis._
import scalaxb._

import java.net.{URI, URL}

import scala.xml.{Elem, NodeSeq}

/**
  *
  * @param rasterSourcesModel Model of layers we can report
  * @param tileMatrixModel Model of tile matrix set
  * @param serviceUrl URL where this service can be reached with addition of `?request=` query parameter
  */
class CapabilitiesView(
  rasterSourcesModel: RasterSourcesModel,
  tileMatrixModel: TileMatrixModel,
  serviceUrl: URL
) {

  def toXML: Elem = {
    import opengis.ows._
    import opengis.wmts._
    import CapabilitiesView._

    val serviceIdentification =
      ServiceIdentification(
        Title = LanguageStringType("GeoTrellis Web Map Tile Service") :: Nil,
        Abstract = LanguageStringType("GeoTrellis Web Map Tile Service") :: Nil,
        Keywords = KeywordsType(LanguageStringType("GeoTrellis") :: LanguageStringType("WMTS") :: LanguageStringType("map") :: Nil, None) :: Nil,
        ServiceType = CodeType("OGC WMTS"),
        ServiceTypeVersion = "1.0.0" :: Nil
      )

    val serviceProvider =
      ServiceProvider(
        ProviderName = "Azavea, Inc.",
        ServiceContact = ResponsiblePartySubsetType()
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
                Constraint = DomainType(
                  possibleValuesOption1 = scalaxb.DataRecord(
                    Some("ows"),
                    Some("ows:AllowedValues"),
                    AllowedValues(
                      scalaxb.DataRecord(
                        Some("ows"),
                        Some("ows:Value"),
                        ValueType("KVP")
                      ) :: Nil
                    )
                  ),
                  attributes = Map("@name" -> "GetEncoding")
                ) :: Nil,
                attributes = Map("@{http://www.w3.org/1999/xlink}href" -> scalaxb.DataRecord(serviceUrl.toURI))
              )) :: Nil)
          )
        ) :: Nil,
        attributes = Map("@name" -> scalaxb.DataRecord("GetCapabilities"))
      )

      val getTile = Operation(
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
        attributes = Map("@name" -> scalaxb.DataRecord("GetTile"))
      )

      val getFeatureInfo = Operation(
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
        attributes = Map("@name" -> scalaxb.DataRecord("GetFeatureInfo"))
      )

      OperationsMetadata(
        Operation = getCapabilities :: getTile :: getFeatureInfo :: Nil
      )
    }

    val layers = modelAsLayers(rasterSourcesModel)
    val tileMatrixSets = tileMatrixModel.matrices.map(_.toXml)

    // that's how layers metadata is generated
    val contents = ContentsType(
      DatasetDescriptionSummary = layers,
      TileMatrixSet = tileMatrixSets
    )

    val ret: NodeSeq = scalaxb.toXML[opengis.wmts.Capabilities](
      obj = Capabilities(
        ServiceIdentification = Some(serviceIdentification),
        ServiceProvider       = Some(serviceProvider),
        OperationsMetadata    = Some(operationsMetadata),
        Contents              = Some(contents)
      ),
      namespace = None,
      elementLabel = Some("Capabilities"),
      scope = constrainedWMTSScope,
      typeAttribute = false
    )

    ret.asInstanceOf[scala.xml.Elem]
  }
}

object CapabilitiesView {
  import opengis.wmts._
  import opengis.ows._

  implicit def toRecord[T: CanWriteXML](t: T): scalaxb.DataRecord[T] = scalaxb.DataRecord(t)

  def boundingBox(extent: Extent): WGS84BoundingBoxType =
    WGS84BoundingBoxType(
      LowerCorner = Seq(extent.xmin, extent.ymin),
      UpperCorner = Seq(extent.xmax, extent.ymax)
    )

  implicit class OgcSourceMethods(val self: OgcSource) {
    def toLayerType(layerName: String): LayerType = {
      val wgs84extent: Extent = ReprojectRasterExtent(self.nativeRE, self.nativeCrs.head, LatLng).extent

      LayerType(
        Title = LanguageStringType(layerName) :: Nil,
        Abstract = Nil,
        Keywords= Nil,
        WGS84BoundingBox = List(boundingBox(wgs84extent)),
        Identifier = CodeType(layerName),
        BoundingBox = Nil,
        Metadata = Nil,
        DatasetDescriptionSummary = Nil,
        Style = List(Style(
          Title=List(LanguageStringType("Style")),
          Abstract=List(LanguageStringType("AbstractStyle")),
          Identifier=CodeType("StyleID"),
          Keywords=Nil,
          LegendURL=Nil
        )),
        Format = List("image/png", "image/jpeg"),
        InfoFormat = List("text/xml"),
        Dimension = Nil,
        // NOTE: This "ID" MUST correspond to the TileMatrixSet ID for the layers to show up in QGIS
        TileMatrixSetLink = List(TileMatrixSetLink("GoogleMapsCompatible")),
        ResourceURL = Nil
      )
    }
  }

  def modelAsLayers(model: RasterSourcesModel): List[scalaxb.DataRecord[LayerType]] = {
    model
      .sourceLookup
      .map { case (key, value) => scalaxb.DataRecord(Some("wms"), Some("Layer"), value.toLayerType(key)) }
      .toList
  }
}
