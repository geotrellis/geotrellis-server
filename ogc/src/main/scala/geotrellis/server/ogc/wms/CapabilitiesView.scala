package geotrellis.server.ogc.wms

import geotrellis.server.ogc._

import geotrellis.proj4.{CRS, LatLng}
import geotrellis.raster.CellSize
import geotrellis.vector.Extent

import cats.syntax.option._
import opengis.wms._
import opengis._
import scalaxb._

import java.net.URL
import scala.xml.Elem

/**
  *
  * @param model Model of layers we can report
  * @param serviceUrl URL where this service can be reached with addition of `?request=` query parameter
  */
class CapabilitiesView(
  model: WmsModel,
  serviceUrl: URL
) {
  import CapabilitiesView._

  def toXML: Elem = {
    val capability = {
      val getCapabilities = OperationType(
        Format = List("text/xml"),
        DCPType = List(DCPType(
          HTTP(Get = Get(OnlineResource(Map(
            "@{http://www.w3.org/1999/xlink}href" -> DataRecord(serviceUrl.toURI),
            "@{http://www.w3.org/1999/xlink}type" -> DataRecord(xlink.Simple: xlink.TypeType)))))
        )))

      val getMap = OperationType(
        Format = List("image/png", "image/jpeg"),
        DCPType = List(DCPType(
          HTTP(Get = Get(OnlineResource(Map(
            "@{http://www.w3.org/1999/xlink}href" -> DataRecord(serviceUrl.toURI),
            "@{http://www.w3.org/1999/xlink}type" -> DataRecord(xlink.Simple: xlink.TypeType)))))
        )))

      Capability(
        Request = Request(GetCapabilities = getCapabilities, GetMap = getMap, GetFeatureInfo = None),
        Exception = Exception(List("XML", "INIMAGE", "BLANK")),
        Layer = modelAsLayer(model.parentLayerMeta, model).some
      )
    }

    scalaxb.toXML[opengis.wms.WMS_Capabilities](
      obj = WMS_Capabilities(model.serviceMeta, capability, Map("@version" -> DataRecord("1.3.0"))),
      namespace = None,
      elementLabel = "WMS_Capabilities".some,
      scope = constrainedWMSScope,
      typeAttribute = false
    ).asInstanceOf[scala.xml.Elem]
  }
}

object CapabilitiesView {
  implicit def toRecord[T: CanWriteXML](t: T): DataRecord[T] = DataRecord(t)

  def boundingBox(crs: CRS, extent: Extent, cellSize: CellSize): BoundingBox =
    if (crs == LatLng)
      BoundingBox(Map(
        "@CRS" -> s"EPSG:${crs.epsgCode.get}",
        "@minx" -> extent.ymin,
        "@miny" -> extent.xmin,
        "@maxx" -> extent.ymax,
        "@maxy" -> extent.xmax,
        "@resx" -> cellSize.width,
        "@resy" -> cellSize.height
      ))
    else
      BoundingBox(Map(
        "@CRS" -> s"EPSG:${crs.epsgCode.get}",
        "@minx" -> extent.xmin,
        "@miny" -> extent.ymin,
        "@maxx" -> extent.xmax,
        "@maxy" -> extent.ymax,
        "@resx" -> cellSize.width,
        "@resy" -> cellSize.height
      ))

  implicit class StyleMethods(val style: OgcStyle) {
    def render(): Style =
      Style(
        Name = style.name,
        Title = style.title,
        LegendURL = style.legends.map(_.toLegendURL)
      )
  }

  implicit class RasterSourceMethods(val source: OgcSource) {
    def toLayer(layerName: String, parentProjections: List[CRS]): Layer = {
      Layer(
        Name = Some(layerName),
        Title = layerName,
        Abstract = Some(layerName),
        KeywordList = None,
        // extra CRS that is supported by this layer
        CRS = (parentProjections ++ source.nativeCrs).distinct.map { crs =>
          crs.epsgCode
            .map { code => s"EPSG:$code" }
            .getOrElse(throw new java.lang.Exception(s"Unable to construct EPSG code from $crs"))
        },
        EX_GeographicBoundingBox = {
          val llExtent = source.extentIn(LatLng)
          Some(EX_GeographicBoundingBox(llExtent.xmin, llExtent.xmax, llExtent.ymin, llExtent.ymax))
        },
        BoundingBox = Nil,
        Dimension = Nil,
        Attribution = None,
        AuthorityURL = Nil,
        Identifier = Nil,
        MetadataURL = Nil,
        DataURL = Nil,
        FeatureListURL = Nil,
        Style = source.styles.map(_.render),
        MinScaleDenominator = None,
        MaxScaleDenominator = None,
        Layer = Nil,
        attributes = Map("@queryable" -> DataRecord(false))
      )
    }
  }

  def modelAsLayer(parentLayerMeta: WmsParentLayerMeta, model: WmsModel): Layer = {
    Layer(
      Name        = parentLayerMeta.name,
      Title       = parentLayerMeta.title,
      Abstract    = parentLayerMeta.description,
      KeywordList = None,
      // All layers are avail at least at this CRS
      // All sublayers would have metadata in this CRS + its own
      CRS = parentLayerMeta.supportedProjections.distinct.map { crs =>
        crs.epsgCode
          .map { code => s"EPSG:$code" }
          .getOrElse(throw new java.lang.Exception(s"Unable to construct EPSG code from $crs"))
      },
      // Extent of all layers in default CRS
      // Should it be world extent? To simplify tests and QGIS work it's all RasterSources extent
      EX_GeographicBoundingBox = {
        val llExtents = model.sourceLookup.map { case (_, src) =>
          src.extentIn(LatLng)
        }
        val llExtent = llExtents.tail.fold(llExtents.head)(_ combine _)
        Some(EX_GeographicBoundingBox(llExtent.xmin, llExtent.xmax, llExtent.ymin, llExtent.ymax))
      },
      // TODO: bounding box for global layer
      BoundingBox         = Nil,
      Dimension           = Nil,
      Attribution         = None,
      AuthorityURL        = Nil,
      Identifier          = Nil,
      MetadataURL         = Nil,
      DataURL             = Nil,
      FeatureListURL      = Nil,
      Style               = Nil,
      MinScaleDenominator = None,
      MaxScaleDenominator = None,
      Layer = model.sourceLookup.map { case (name, src) => src.toLayer(name, parentLayerMeta.supportedProjections) }.toSeq,
      attributes = Map("@queryable" -> DataRecord(false))
    )
  }
}
