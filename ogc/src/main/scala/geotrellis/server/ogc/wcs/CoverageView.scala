package geotrellis.server.ogc.wcs

import geotrellis.server.ogc.ows.OwsDataRecord
import geotrellis.proj4.LatLng
import geotrellis.raster.reproject.ReprojectRasterExtent
import geotrellis.raster.{Dimensions, GridExtent}
import geotrellis.server.ogc.{MapAlgebraSource, OgcSource, SimpleSource, URN}

import cats.syntax.option._
import opengis.ows._
import opengis.wcs._
import opengis._
import scalaxb._

import scala.xml.Elem
import java.net.{URI, URL}

class CoverageView(
  wcsModel: WcsModel,
  serviceUrl: URL,
  identifiers: Seq[String]
) {
  def toXML: Elem = {
    val sources = if(identifiers == Nil) wcsModel.sources else wcsModel.sourceLookup.filterKeys(identifiers.contains).values
    scalaxb.toXML[CoverageDescriptions](
      obj           = CoverageDescriptions(sources.toList.map(CoverageView.sourceDescription)),
      namespace     = None,
      elementLabel  = "CoverageDescriptions".some,
      scope         = constrainedWCSScope,
      typeAttribute = false
    ).asInstanceOf[Elem]
  }
}

object CoverageView {
  def sourceDescription(source: OgcSource): CoverageDescriptionType = {
    val identifier = source.name

    val nativeCrs = source.nativeCrs.head
    val re = source.nativeRE
    val llre = source match {
      case SimpleSource(name, title, rs, styles) =>
        ReprojectRasterExtent(rs.gridExtent, rs.crs, LatLng)
      case MapAlgebraSource(name, title, rss, algebra, styles) =>
        rss.values.map { rs =>
          ReprojectRasterExtent(rs.gridExtent, rs.crs, LatLng)
        }.reduce({ (re1, re2) =>
          val e = re1.extent combine re2.extent
          val cs = if (re1.cellSize.resolution < re2.cellSize.resolution) re1.cellSize else re2.cellSize
          new GridExtent[Long](e, cs)
        })
    }
    val ex = re.extent
    val llex = llre.extent
    val Dimensions(w, h) = re.dimensions

    CoverageDescriptionType(
      Title      = LanguageStringType(source.title) :: Nil,
      Abstract   = Nil,
      Keywords   = Nil,
      Identifier = identifier,
      Metadata   = Nil,
      Domain     = CoverageDomainType(
        SpatialDomain = SpatialDomainType(
          BoundingBox = OwsDataRecord(
            BoundingBoxType(
              LowerCorner = 0D :: 0D :: Nil,
              UpperCorner = w.toDouble :: h.toDouble :: Nil,
              attributes  = Map(
                "@crs"        -> DataRecord(new URI("urn:ogc:def:crs:OGC::imageCRS")),
                "@dimensions" -> DataRecord(BigInt(2))
              )
            )
          ) :: OwsDataRecord(
            BoundingBoxType(
              LowerCorner = ex.xmin :: ex.ymin :: Nil,
              UpperCorner = ex.xmax :: ex.ymax :: Nil,
              attributes  = Map(
                "@crs"        -> DataRecord(new URI(URN.unsafeFromCrs(nativeCrs))),
                "@dimensions" -> DataRecord(BigInt(2))
              )
            )
          ) :: OwsDataRecord(
            BoundingBoxType(
              LowerCorner = llex.ymin :: llex.xmin :: Nil,
              UpperCorner = llex.ymax :: llex.xmax :: Nil,
              attributes  = Map(
                "@crs" -> DataRecord(new URI(URN.unsafeFromCrs(LatLng))),
                "@dimensions" -> DataRecord(BigInt(2))
              )
            )
          ) :: OwsDataRecord(
            WGS84BoundingBoxType(
              LowerCorner = llex.ymin :: llex.xmin :: Nil,
              UpperCorner = llex.ymax :: llex.xmax :: Nil,
              attributes  = Map(
                "@dimensions" -> DataRecord(BigInt(2))
              )
            )
          ) :: Nil,
          GridCRS = Some(GridCrsType(
            GridBaseCRS = new URI(URN.unsafeFromCrs(nativeCrs)),
            GridType    = new URI("urn:ogc:def:method:WCS:1.1:2dSimpleGrid").some,
            GridOrigin  = List(ex.xmin, ex.ymax).some,
            GridOffsets = re.cellheight :: -re.cellwidth :: Nil,
            GridCS      = new URI("urn:ogc:def:cs:OGC:0.0:Grid2dSquareCS").some
          ))
        )
      ),
      RangeValue = wcs.RangeType(
        Field = FieldType(
          Identifier = "contents",
          Definition = UnNamedDomainType(possibleValuesOption1 = OwsDataRecord(AnyValue())),
          InterpolationMethods  = InterpolationMethods(
            InterpolationMethod =
              InterpolationMethodType("nearest neighbor") ::
              InterpolationMethodType("bilinear") ::
              InterpolationMethodType("bicubic") :: Nil,
            Default = "nearest neighbor".some
          )
        ) :: Nil
      ),
      SupportedCRS =
        new URI(URN.unsafeFromCrs(nativeCrs)) ::
        new URI(URN.unsafeFromCrs(LatLng)) ::
        new URI("urn:ogc:def:crs:OGC::imageCRS") :: Nil,
      SupportedFormat = "image/geotiff" :: "image/jpeg" :: "image/png" :: Nil
    )
  }

  def apply(wcsModel: WcsModel, serviceUrl: URL): CoverageView = new CoverageView(wcsModel, serviceUrl, Nil)
  def apply(wcsModel: WcsModel, serviceUrl: URL, params: DescribeCoverageWcsParams): CoverageView = new CoverageView(wcsModel, serviceUrl, params.identifiers)
}
