package geotrellis.server.ogc.wmts

import geotrellis.proj4.{CRS, LatLng}
import geotrellis.contrib.vlm.RasterSource
import geotrellis.vector.Extent

import opengis._
import scalaxb._

import java.net.{URI, URL}

import scala.xml.{Elem, NodeSeq}

/**
  *
  * @param model Model of layers we can report
  * @param serviceUrl URL where this service can be reached with addition of `?request=` query parameter
  * @param defaultCrs Common CRS, all layers must be available in at least this CRS
  */
class CapabilitiesView(model: RasterSourcesModel, serviceUrl: URL, defaultCrs: CRS = LatLng) {

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

    val (layers, tileMatrixSet) = modelAsLayers(model, defaultCrs)

    // that's how layers metadata is generated
    val contents = ContentsType(
      DatasetDescriptionSummary = layers,
      TileMatrixSet = tileMatrixSet :: Nil
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

  implicit class RasterSourceMethods(val self: RasterSource) {
    def toLayer(layerName: String, defaultCrs: CRS = LatLng): LayerType =
      LayerType(
        Title = LanguageStringType(layerName) :: Nil,
        Abstract = Nil,
        Keywords= Nil,
        WGS84BoundingBox = Set(self.crs, defaultCrs).toList.map { crs =>
          val rs = self.reproject(crs)
          boundingBox(rs.extent)
        },
        Identifier = CodeType(layerName),
        BoundingBox = Nil,
        Metadata = Nil,
        DatasetDescriptionSummary = Nil,
        Style = Nil,
        Format = List("image/png", "image/jpeg"),
        InfoFormat = List("text/xml"),
        Dimension = Nil,
        TileMatrixSetLink = List(TileMatrixSetLink(layerName)),
        ResourceURL = Nil
      )

    // to make it work we need to know information about layout
    def toTileMatrix(layerName: String, defaultCRS: CRS = LatLng): TileMatrix = {
      TileMatrix(
        Title = LanguageStringType(layerName) :: Nil,
        Abstract = LanguageStringType(layerName) :: Nil,
        Keywords = Nil,
        Identifier = CodeType(layerName),
        ScaleDenominator = 1e-6,
        TopLeftCorner = List(self.extent.xmin, self.extent.ymax),
        TileWidth = 256,
        TileHeight = 256,
        MatrixWidth = 10000,
        MatrixHeight = 10000
      )
    }
  }

  def modelAsLayers(model: RasterSourcesModel, crs: CRS = LatLng): (List[scalaxb.DataRecord[LayerType]], TileMatrixSet) = {
    val layers = model.map.map { case (key, value) => scalaxb.DataRecord(Some("wms"), Some("Layer"), value.toLayer(key)) }.toList
    val matrixList = model.map.map { case (key, value) => value.toTileMatrix(key) }.toList

    layers -> TileMatrixSet(
      Title = LanguageStringType("GeoTrellis WMTS Layer") :: Nil,
      Abstract = LanguageStringType("GeoTrellis WMTS Layer") :: Nil,
      Keywords = Nil,
      Identifier = CodeType("GeoTrellis WMTS Layer"),
      // TODO: bounding box for global layer
      BoundingBox = None,
      SupportedCRS = new URI(s"urn:ogc:def:crs:OGC:1.3:${crs.epsgCode.map { code => s"EPSG:$code" }.getOrElse("CRS84")}"),
      WellKnownScaleSet = None,
      TileMatrix = matrixList
    )
  }
}
