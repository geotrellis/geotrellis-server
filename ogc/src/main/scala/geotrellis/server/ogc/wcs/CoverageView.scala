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
import geotrellis.server.ogc.{MapAlgebraSource, OgcSource, OgcTimeEmpty, OgcTimeInterval, OgcTimePositions, OutputFormat, RasterOgcSource, URN}
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
    val sources = if (identifiers == Nil) wcsModel.sources.store else wcsModel.sources.find(withNames(identifiers.toSet))
    val sourcesMap: F[Map[String, List[OgcSource]]] = sources.map(_.groupBy(_.name))
    val coverageTypeMap = sourcesMap.map(_.map { case (key, value) => key -> CoverageView.sourceDescription(wcsModel.supportedProjections, value) })
    coverageTypeMap.map { coverageType =>
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
  private def extractGridExtent(source: OgcSource, targetCRS: CRS): GridExtent[Long] =
    source match {
      case mas: MapAlgebraSource =>
        mas.sourcesList
          .map { rs =>
            ReprojectRasterExtent(
              rs.gridExtent,
              rs.crs,
              targetCRS,
              Options.DEFAULT.copy(mas.resampleMethod)
            )
          }
          .reduce { (re1, re2) =>
            val e = re1.extent.combine(re2.extent)
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
          targetCRS,
          Options.DEFAULT.copy(rasterOgcLayer.resampleMethod)
        )
    }

  def sourceDescription(supportedProjections: List[CRS], sources: List[OgcSource]): CoverageDescriptionType = {
    val source = sources.head
    val nativeCrs = source.nativeCrs.head
    val re = source.nativeRE
    val ex = re.extent
    val Dimensions(w, h) = re.dimensions

    /**
     * WCS expects this very specific format for its time strings, which is not quite (TM) what Java's toString method returns. Instead we convert to
     * Instant.toString, which does conform.
     *
     * The [ISO 8601:2000] syntax for dates and times may be summarized by the following template (see Annex D of the OGC Web Map Service [OGC
     * 06-042]): ccyy-mm-ddThh:mm:ss.sssZ Where ― ccyy-mm-dd is the date (a four-digit year, and a two-digit month and day); ― Tis a separator between
     * the data and time strings; ― hh:mm:ss.sss is the time (a two-digit hour and minute, and fractional seconds); ― Z represents the Coordinated
     * Universal Time (UTC or ―zulu‖) time zone.
     *
     * This was excerpted from "WCS Implementation Standard 1.1" available at: https://portal.ogc.org/files/07-067r5
     */
    val temporalDomain: Option[TimeSequenceType] = {
      val records = source.time match {
        case otp: OgcTimePositions => otp.toList.map(p => GmlDataRecord(TimePositionType(p)))
        case OgcTimeInterval(start, end, period) =>
          GmlDataRecord(
            wcs.TimePeriodType(
              BeginPosition = TimePositionType(start.toInstant.toString),
              EndPosition = TimePositionType(end.toInstant.toString),
              TimeResolution = period.map(_.toString)
            )
          ) :: Nil
        case OgcTimeEmpty => Nil
      }
      if (records.nonEmpty) TimeSequenceType(records).some else None
    }

    val uniqueCrs: List[CRS] = (nativeCrs :: LatLng :: supportedProjections).distinct

    CoverageDescriptionType(
      Title = LanguageStringType(source.title) :: Nil,
      AbstractValue = Nil,
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
                "@crs" -> DataRecord(new URI("urn:ogc:def:crs:OGC::imageCRS")),
                "@dimensions" -> DataRecord(BigInt(2))
              )
            )
          ) :: uniqueCrs.flatMap {
            case crs if crs == LatLng =>
              val lex = extractGridExtent(source, crs).extent
              OwsDataRecord(
                BoundingBoxType(
                  LowerCorner = lex.ymin :: lex.xmin :: Nil,
                  UpperCorner = lex.ymax :: lex.xmax :: Nil,
                  attributes = Map(
                    "@crs" -> DataRecord(new URI(URN.unsafeFromCrs(crs))),
                    "@dimensions" -> DataRecord(BigInt(2))
                  )
                )
              ) :: OwsDataRecord(
                WGS84BoundingBoxType(
                  LowerCorner = lex.ymin :: lex.xmin :: Nil,
                  UpperCorner = lex.ymax :: lex.xmax :: Nil,
                  attributes = Map(
                    "@dimensions" -> DataRecord(BigInt(2))
                  )
                )
              ) :: Nil
            case crs if crs.isGeographic =>
              val lex = extractGridExtent(source, crs).extent
              OwsDataRecord(
                BoundingBoxType(
                  LowerCorner = lex.ymin :: lex.xmin :: Nil,
                  UpperCorner = lex.ymax :: lex.xmax :: Nil,
                  attributes = Map(
                    "@crs" -> DataRecord(new URI(URN.unsafeFromCrs(crs))),
                    "@dimensions" -> DataRecord(BigInt(2))
                  )
                )
              ) :: Nil
            case crs =>
              val lex = extractGridExtent(source, crs).extent
              OwsDataRecord(
                BoundingBoxType(
                  LowerCorner = lex.xmin :: lex.ymin :: Nil,
                  UpperCorner = lex.xmax :: lex.ymax :: Nil,
                  attributes = Map(
                    "@crs" -> DataRecord(new URI(URN.unsafeFromCrs(crs))),
                    "@dimensions" -> DataRecord(BigInt(2))
                  )
                )
              ) :: Nil
          },
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
            InterpolationMethod = InterpolationMethodType("nearest-neighbor") ::
              InterpolationMethodType("bilinear") ::
              InterpolationMethodType("cubic-convolution") ::
              InterpolationMethodType("cubic-spline") ::
              InterpolationMethodType("lanczos") :: Nil,
            Default = "nearest-neighbor".some
          )
        ) :: Nil
      ),
      SupportedCRS = new URI("urn:ogc:def:crs:OGC::imageCRS") :: uniqueCrs.flatMap(proj => URN.fromCrs(proj).map(new URI(_))),
      SupportedFormat = OutputFormat.all.reverse
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
