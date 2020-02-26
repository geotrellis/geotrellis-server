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

import geotrellis.store.query._
import geotrellis.server.ogc.ows.OwsDataRecord
import geotrellis.proj4.LatLng
import geotrellis.raster.reproject.ReprojectRasterExtent
import geotrellis.raster.{Dimensions, GridExtent}
import geotrellis.server.ogc.{MapAlgebraSource, OgcSource, SimpleSource, URN}
import cats.syntax.option._
import java.net.{URI, URL}

import geotrellis.server.ogc.gml.GmlDataRecord
import opengis.gml._
import opengis.ows._
import opengis.wcs._
import opengis._

import scala.xml.Elem
import scalaxb._


class CoverageView(
  wcsModel: WcsModel,
  serviceUrl: URL,
  identifiers: Seq[String]
) {
  def toXML: Elem = {
    val sources = if(identifiers == Nil) wcsModel.sources.store else wcsModel.sources.find(withNames(identifiers.toSet))
    val sourcesMap: Map[String, List[OgcSource]] = sources.groupBy(_.name)
    val coverageTypeMap = sourcesMap.mapValues(CoverageView.sourceDescription(_))
    scalaxb.toXML[CoverageDescriptions](
      obj           = CoverageDescriptions(coverageTypeMap.values.toList),
      namespace     = None,
      elementLabel  = "CoverageDescriptions".some,
      scope         = constrainedWCSScope,
      typeAttribute = false
    ).asInstanceOf[Elem]
  }
}

object CoverageView {

  def sourceDescription(sources: List[OgcSource]): CoverageDescriptionType = {
    val source = sources.head
    val nativeCrs = source.nativeCrs.head
    val re = source.nativeRE
    val llre = source match {
      case SimpleSource(_, _, rs, _, _) =>
        ReprojectRasterExtent(rs.gridExtent, rs.crs, LatLng)
      case MapAlgebraSource(_, _, rss, _, _, _) =>
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
    val temporalInstants = sources.flatMap { s =>
      s.time.map(t => GmlDataRecord(TimePositionType(t.toWcsIsoString)))
    }
    val temporalDomain: Option[TimeSequenceType] = if (temporalInstants.length > 0) {
      Some(TimeSequenceType(temporalInstants))
    } else {
      None
    }

    CoverageDescriptionType(
      Title      = LanguageStringType(source.title) :: Nil,
      Abstract   = Nil,
      Keywords   = Nil,
      Identifier = source.name,
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
        ),
        TemporalDomain = temporalDomain
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
