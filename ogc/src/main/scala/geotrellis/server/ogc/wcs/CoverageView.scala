package geotrellis.server.ogc.wcs

import geotrellis.server.ogc.wcs.params.DescribeCoverageWcsParams

import geotrellis.proj4.LatLng
import geotrellis.raster.reproject.ReprojectRasterExtent
import geotrellis.raster.{Dimensions, GridExtent}
import geotrellis.server.ogc.{MapAlgebraSource, OgcSource, SimpleSource, URN}

import shapeless.syntax.std.tuple._
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
  import CoverageView._

  def toXML: Elem = {
    val sources = if(identifiers == Nil) wcsModel.sources else wcsModel.sourceLookup.filterKeys(identifiers.contains).values
    scalaxb.toXML[opengis.wcs.CoverageDescriptions](
      obj           = CoverageDescriptions(sources.toList.map(_.description)),
      namespace     = None,
      elementLabel  = Some("CoverageDescriptions"),
      scope         = constrainedWCSScope,
      typeAttribute = false
    ).asInstanceOf[scala.xml.Elem]
  }
}

object CoverageView {
  implicit def toRecord[T: CanWriteXML](t: T): scalaxb.DataRecord[T] = scalaxb.DataRecord(t)

  implicit class OgcSourceMethods(val self: OgcSource) {
    def description: CoverageDescriptionType = {
      val identifier = self.name

      val nativeCrs = self.nativeCrs.head
      val re = self.nativeRE
      val llre = self match {
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
      val Dimensions(w, h) = llre.dimensions

      CoverageDescriptionType(
        Title    = LanguageStringType(self.title) :: Nil,
        Abstract = Nil,
        Keywords = Nil,
        Identifier = identifier,
        Metadata = Nil,
        Domain = CoverageDomainType(
          SpatialDomain = SpatialDomainType(
            BoundingBox = scalaxb.DataRecord(
              Some("ows"),
              Some("ows:BoundingBox"),
              BoundingBoxType(
                LowerCorner = 0D :: 0D :: Nil,
                UpperCorner = w.toDouble :: h.toDouble :: Nil,
                attributes = Map(
                  "@crs" -> scalaxb.DataRecord(new URI("urn:ogc:def:crs:OGC::imageCRS")),
                  "@dimensions" -> scalaxb.DataRecord(BigInt(2))
                )
              )
            ) :: scalaxb.DataRecord(
              Some("ows"),
              Some("ows:BoundingBox"),
              BoundingBoxType(
                LowerCorner = ex.xmin :: ex.ymin :: Nil,
                UpperCorner = ex.xmax :: ex.ymax :: Nil,
                attributes = Map(
                  "@crs" -> scalaxb.DataRecord(new URI(URN.unsafeFromCrs(nativeCrs))),
                  "@dimensions" -> scalaxb.DataRecord(BigInt(2))
                )
              )
            ) :: scalaxb.DataRecord(
              Some("ows"),
              Some("ows:BoundingBox"),
              BoundingBoxType(
                LowerCorner = llex.xmin :: llex.ymin :: Nil,
                UpperCorner = llex.xmax :: llex.ymax :: Nil,
                attributes = Map(
                  "@crs" -> scalaxb.DataRecord(new URI(URN.unsafeFromCrs(LatLng))),
                  "@dimensions" -> scalaxb.DataRecord(BigInt(2))
                )
              )
            ) :: Nil,
            GridCRS = Some(GridCrsType(
              GridBaseCRS = new URI(URN.unsafeFromCrs(nativeCrs)),
              GridType    = Some(new URI("urn:ogc:def:method:WCS:1.1:2dGridIn2dCrs")),
              GridOrigin  = Some(re.gridToMap(0, h - 1).toList),
              GridOffsets = List(re.cellwidth, -re.cellheight),
              GridCS      = Some(new URI("urn:ogc:def:cs:OGC:0.0:Grid2dSquareCS"))
            ))
          )
        ),
        RangeValue = wcs.RangeType(
          Field = FieldType(
            Identifier = "contents",
            Definition = UnNamedDomainType(possibleValuesOption1 = scalaxb.DataRecord(
              Some("ows"),
              Some("ows:AnyValue"),
              AnyValue()
            )),
            InterpolationMethods = InterpolationMethods(
              InterpolationMethod =
                InterpolationMethodType("nearest neighbor") ::
                InterpolationMethodType("bilinear") ::
                InterpolationMethodType("bicubic") :: Nil,
              Default = Some("nearest neighbor")
            )
          ) :: Nil
        ),
        SupportedCRS = new URI(URN.unsafeFromCrs(nativeCrs)) :: Nil,
        SupportedFormat = "image/GeoTIFF" :: "image/JPEG" :: "image/PNG" :: Nil
      )
    }
  }

  def apply(wcsModel: WcsModel, serviceUrl: URL): CoverageView = new CoverageView(wcsModel, serviceUrl, Nil)
  def apply(wcsModel: WcsModel, serviceUrl: URL, params: DescribeCoverageWcsParams): CoverageView = new CoverageView(wcsModel, serviceUrl, params.identifiers)
}
