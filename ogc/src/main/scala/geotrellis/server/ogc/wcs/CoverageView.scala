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
import geotrellis.server.ogc.{MapAlgebraSource, OgcSource, OgcTimeEmpty, OgcTimeInterval, OgcTimePositions, RasterOgcSource, URN}
import geotrellis.server.ogc.ows.OwsDataRecord
import geotrellis.server.ogc.gml.GmlDataRecord
import geotrellis.proj4.LatLng
import geotrellis.raster.reproject.ReprojectRasterExtent
import geotrellis.raster.reproject.Reproject.Options
import geotrellis.raster.{Dimensions, GridExtent}
import opengis.gml._
import opengis.ows._
import opengis.wcs._
import opengis._

import cats.Functor
import cats.syntax.functor._
import cats.syntax.option._

import scala.xml.Elem
import scalaxb._
import java.net.{URI, URL}
import geotrellis.proj4.CRS

class CoverageView[F[_]: Functor](wcsModel: WcsModel[F], serviceUrl: URL, identifiers: Seq[String]) {
  def toXML: F[Elem] = {
    val sources                                     = if (identifiers == Nil) wcsModel.sources.store else wcsModel.sources.find(withNames(identifiers.toSet))
    val sourcesMap: F[Map[String, List[OgcSource]]] = sources.map(_.groupBy(_.name))
    val coverageTypeMap                             = sourcesMap.map(_.mapValues(CoverageView.sourceDescription(wcsModel.supportedProjections, _)))
    coverageTypeMap map { coverageType =>
      scalaxb
        .toXML[CoverageDescriptions](
          obj = CoverageDescriptions(coverageType.values.toList),
          namespace = None,
          elementLabel = "CoverageDescriptions".some,
          scope = wcsScope,
          typeAttribute = false
        )
        .asInstanceOf[Elem]
    }
  }
}

object CoverageView {

  def sourceDescription(supportedProjections: List[CRS], sources: List[OgcSource]): CoverageDescriptionType = {
    val source           = sources.head
    val nativeCrs        = source.nativeCrs.head
    val re               = source.nativeRE
    val llre             = source match {
      case mas: MapAlgebraSource           =>
        mas.sourcesList
          .map { rs =>
            ReprojectRasterExtent(
              rs.gridExtent,
              rs.crs,
              LatLng,
              Options.DEFAULT.copy(mas.resampleMethod)
            )
          }
          .reduce { (re1, re2) =>
            val e  = re1.extent combine re2.extent
            val cs =
              if (re1.cellSize.resolution < re2.cellSize.resolution)
                re1.cellSize
              else re2.cellSize
            new GridExtent[Long](e, cs)
          }
      case rasterOgcLayer: RasterOgcSource =>
        val rs = rasterOgcLayer.source
        ReprojectRasterExtent(
          rs.gridExtent,
          rs.crs,
          LatLng,
          Options.DEFAULT.copy(rasterOgcLayer.resampleMethod)
        )
    }
    val ex               = re.extent
    val llex             = llre.extent
    val Dimensions(w, h) = re.dimensions

    /** WCS expects this very specific format for its time strings, which is not quite (TM)
      * what Java's toString method returns. Instead we convert to Instant.toString, which
      * does conform.
      *
      * The [ISO 8601:2000] syntax for dates and times may be summarized by the following template
      * (see Annex D of the OGC Web Map Service [OGC 06-042]):
      * ccyy-mm-ddThh:mm:ss.sssZ
      * Where
      * ― ccyy-mm-dd is the date (a four-digit year, and a two-digit month and day);
      * ― Tis a separator between the data and time strings;
      * ― hh:mm:ss.sss is the time (a two-digit hour and minute, and fractional seconds);
      * ― Z represents the Coordinated Universal Time (UTC or ―zulu‖) time zone.
      *
      * This was excerpted from "WCS Implementation Standard 1.1" available at:
      * https://portal.ogc.org/files/07-067r5
      */
    val temporalDomain: Option[TimeSequenceType] = {
      val records = source.time match {
        case OgcTimePositions(nel)                          =>
          nel.toList.map { t =>
            GmlDataRecord(TimePositionType(t.toInstant.toString))
          }
        case OgcTimeInterval(start, end, _) if start == end =>
          GmlDataRecord(TimePositionType(start.toInstant.toString)) :: Nil
        case OgcTimeInterval(start, end, _)                 =>
          GmlDataRecord(TimePositionType(start.toInstant.toString)) ::
            GmlDataRecord(TimePositionType(end.toInstant.toString)) :: Nil
        case OgcTimeEmpty                                   => Nil
      }
      if (records.nonEmpty) TimeSequenceType(records).some
      else None
    }

    val uniqueCrs: List[CRS] = (
      nativeCrs :: LatLng :: supportedProjections
    ).distinct

    CoverageDescriptionType(
      Title = LanguageStringType(source.title) :: Nil,
      Abstract = Nil,
      Keywords = Nil,
      Identifier = source.name,
      Metadata = Nil,
      Domain = CoverageDomainType(
        SpatialDomain = SpatialDomainType(
          BoundingBox = OwsDataRecord(
            BoundingBoxType(
              LowerCorner = 0d :: 0d :: Nil,
              UpperCorner = w.toDouble :: h.toDouble :: Nil,
              attributes = Map(
                "@crs"        -> DataRecord(new URI("urn:ogc:def:crs:OGC::imageCRS")),
                "@dimensions" -> DataRecord(BigInt(2))
              )
            )
          ) :: OwsDataRecord(
            BoundingBoxType(
              LowerCorner = ex.xmin :: ex.ymin :: Nil,
              UpperCorner = ex.xmax :: ex.ymax :: Nil,
              attributes = Map(
                "@crs"        -> DataRecord(new URI(URN.unsafeFromCrs(nativeCrs))),
                "@dimensions" -> DataRecord(BigInt(2))
              )
            )
          ) :: OwsDataRecord(
            BoundingBoxType(
              LowerCorner = llex.ymin :: llex.xmin :: Nil,
              UpperCorner = llex.ymax :: llex.xmax :: Nil,
              attributes = Map(
                "@crs"        -> DataRecord(new URI(URN.unsafeFromCrs(LatLng))),
                "@dimensions" -> DataRecord(BigInt(2))
              )
            )
          ) :: OwsDataRecord(
            WGS84BoundingBoxType(
              LowerCorner = llex.ymin :: llex.xmin :: Nil,
              UpperCorner = llex.ymax :: llex.xmax :: Nil,
              attributes = Map(
                "@dimensions" -> DataRecord(BigInt(2))
              )
            )
          ) :: Nil,
          GridCRS = Some(
            GridCrsType(
              GridBaseCRS = new URI(URN.unsafeFromCrs(nativeCrs)),
              GridType = new URI("urn:ogc:def:method:WCS:1.1:2dSimpleGrid").some,
              GridOrigin = List(ex.xmin, ex.ymax).some,
              GridOffsets = re.cellheight :: -re.cellwidth :: Nil,
              GridCS = new URI("urn:ogc:def:cs:OGC:0.0:Grid2dSquareCS").some
            )
          )
        ),
        TemporalDomain = temporalDomain
      ),
      RangeValue = wcs.RangeType(
        Field = FieldType(
          Identifier = "contents",
          Definition = UnNamedDomainType(
            possibleValuesOption1 = OwsDataRecord(AnyValue())
          ),
          InterpolationMethods = InterpolationMethods(
            InterpolationMethod = InterpolationMethodType("nearest neighbor") ::
              InterpolationMethodType("bilinear") ::
              InterpolationMethodType("bicubic") :: Nil,
            Default = "nearest neighbor".some
          )
        ) :: Nil
      ),
      SupportedCRS = new URI("urn:ogc:def:crs:OGC::imageCRS") ::
        (uniqueCrs flatMap { proj => URN.fromCrs(proj) map { new URI(_) } }),
      SupportedFormat = "image/geotiff" :: "image/jpeg" :: "image/png" :: Nil
    )
  }

  def apply[F[_]: Functor](
    wcsModel: WcsModel[F],
    serviceUrl: URL
  ): CoverageView[F] =
    new CoverageView(wcsModel, serviceUrl, Nil)

  def apply[F[_]: Functor](
    wcsModel: WcsModel[F],
    serviceUrl: URL,
    params: DescribeCoverageWcsParams
  ): CoverageView[F] =
    new CoverageView[F](wcsModel, serviceUrl, params.identifiers)
}
