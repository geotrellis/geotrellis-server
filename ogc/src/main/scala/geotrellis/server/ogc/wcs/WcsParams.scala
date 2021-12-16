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

import geotrellis.server.ogc._
import geotrellis.proj4.CRS
import geotrellis.raster.{CellSize, GridExtent}
import geotrellis.server.ogc.{OgcTime, OgcTimeInterval, OgcTimePositions, OutputFormat}
import geotrellis.server.ogc.params.ParamError.UnsupportedFormatError
import geotrellis.server.ogc.params._
import geotrellis.store.query._
import geotrellis.vector.{Extent, ProjectedExtent}
import cats.data.Validated._
import cats.data.{NonEmptyList => NEL, Validated, ValidatedNel}
import cats.syntax.apply._
import cats.syntax.traverse._
import cats.syntax.option._
import geotrellis.store.query.vector.ProjectedGeometry

import scala.util.Try
import java.net.URI

sealed abstract class WcsParams {
  val version: String
}

case class GetCapabilitiesWcsParams(version: String) extends WcsParams

case class DescribeCoverageWcsParams(version: String, identifiers: Seq[String]) extends WcsParams

/**
 * "EPSG:4326" or "WGS84" use the latitude first, longitude second axis order. According to the WCS spec for 1.1, some CRS have inverted axis box:
 * 1.0.0: minx,miny,maxx,maxy
 * 1.1.0, 1.1.2: OGC 07-067r5 (WCS 1.1.2) referes to OGC 06-121r3 which says "The number of axes included, and the order of these axes, shall be as
 * specified by the referenced CRS." That means inverted for geographic.
 *
 * Reference to QGIS: https://github.com/qgis/QGIS/blob/final-3_10_2/src/providers/wcs/qgswcsprovider.cpp#L674 Parameters descriptions can be also
 * found here: https://mapserver.org/ogc/wcs_server.html
 *
 * WCS 1.1.1 specs URI: https://portal.opengeospatial.org/files/07-067r2
 */
case class GetCoverageWcsParams(
  version: String,
  identifier: String,
  boundingBox: Extent,
  temporalSequence: List[OgcTime],
  format: OutputFormat,
  gridBaseCRS: Option[CRS],
  gridCS: URI,
  gridType: URI,
  // GridOrigin is BBOX minx, maxy // swapped in case of a geographic projection
  // It is optional, in case it is missing we can use the boundingBox corner
  gridOrigin: Option[(Double, Double)],
  // GridOffsets is xres, yres // swapped in case of a geographic projection
  gridOffsets: Option[(Double, Double)],
  crs: CRS,
  params: ParamMap
) extends WcsParams {

  def toQuery: Query = {
    val query = withName(identifier) and intersects(ProjectedGeometry(extent, crs))
    temporalSequence.headOption match {
      // For now, since a RasterSource maps 1 to 1 to OgcSource, we only create a
      // temporal filter on the first TimeInterval in the list. Revisit when we are
      // able to utilize all requested TimeIntervals.
      case Some(timeInterval: OgcTimeInterval) => query and between(timeInterval.start, timeInterval.end)
      case Some(OgcTimePositions(list))        => query and list.toList.map(at(_)).reduce(_ or _)
      case _                                   => query
    }
  }

  val changeXY: Boolean = crs.isGeographic

  def cellSize: Option[CellSize] =
    if (changeXY) gridOffsets.map { case (f, s) => CellSize(-f, s) }
    else gridOffsets.map { case (f, s) => CellSize(f, -s) }

  /**
   * Shrink the extent to border cells centers by half cell size. GridOrigin: default is "0,0" (KVP) or "0 0" (XML); it is the boundingBox corner.
   */
  def extent: Extent =
    if (changeXY) {
      val (xmax, ymin) = gridOrigin.getOrElse(boundingBox.xmax -> boundingBox.ymin)
      Extent(
        boundingBox.xmin,
        ymin,
        xmax,
        boundingBox.ymax
      ).buffer(cellSize).swapXY
    } else {
      val (xmin, ymax) = gridOrigin.getOrElse(boundingBox.xmin -> boundingBox.ymax)
      Extent(
        xmin,
        boundingBox.ymin,
        boundingBox.xmax,
        ymax
      ).buffer(cellSize)
    }

  def gridExtent: Option[GridExtent[Long]] = cellSize.map(GridExtent[Long](extent, _))
}

object WcsParams {

  val wcsVersion  = "1.1.1"
  val wcsVersions = Set(wcsVersion, "1.1.0")

  /** Defines valid request types, and the WcsParams to build from them. */
  private val requestMap: Map[String, ParamMap => ValidatedNel[ParamError, WcsParams]] =
    Map(
      "getcapabilities"  -> GetCapabilitiesWcsParams.build,
      "describecoverage" -> DescribeCoverageWcsParams.build,
      "getcoverage"      -> GetCoverageWcsParams.build
    )

  private val validRequests = requestMap.keys.toSet

  def apply(queryParams: Map[String, Seq[String]]): ValidatedNel[ParamError, WcsParams] = {
    val params = ParamMap(queryParams)

    val serviceParam         = params.validatedParam("service", validValues = Set("wcs"))
    val requestParam         = params.validatedParam("request", validValues = validRequests)
    val firstStageValidation = (serviceParam, requestParam).mapN { case (_, b) => b }

    firstStageValidation
      // Further validation and building based on request type.
      .andThen(request => requestMap(request)(params))
  }
}

object GetCapabilitiesWcsParams {
  def build(params: ParamMap): ValidatedNel[ParamError, WcsParams] = {
    val versionParam = params.validatedVersion(WcsParams.wcsVersion, WcsParams.wcsVersions)
    versionParam.map { version: String => GetCapabilitiesWcsParams(version) }
  }
}

object DescribeCoverageWcsParams {
  def build(params: ParamMap): ValidatedNel[ParamError, WcsParams] = {
    val versionParam = params.validatedVersion(WcsParams.wcsVersion, WcsParams.wcsVersions)

    versionParam
      .andThen { version: String =>
        params
          .validatedParam("identifiers")
          .map(_.split(",").toSeq)
          .map(ids => (version, ids))
      }
      .map { case (version, identifiers) => DescribeCoverageWcsParams(version, identifiers) }
  }
}

object GetCoverageWcsParams {
  private def getBboxAndCrsOption(params: ParamMap, field: String): ValidatedNel[ParamError, (Vector[Double], Option[String])] =
    params.validatedParam[(Vector[Double], Option[String])](
      field,
      bboxStr =>
        /** Usually the CRS is the 5th element in the bbox param. */
        try {
          val v = bboxStr.split(",").toVector
          if (v.length == 4) (v.map(_.toDouble), None).some
          else if (v.length == 5) (v.take(4).map(_.toDouble), v.last.some).some
          else None
        } catch {
          case _: Throwable => None
        }
    )

  def build(params: ParamMap): ValidatedNel[ParamError, WcsParams] = {
    val versionParam = params.validatedVersion(WcsParams.wcsVersion, WcsParams.wcsVersions)

    versionParam
      .andThen { version: String =>
        /**
         * Collected the bbox, id, and possibly the CRS in one shot. This is because the boundingbox param could contain the CRS as the 5th element.
         */
        val idAndBboxAndCrsOption = {
          val identifier       = params.validatedParam("identifier")
          val bboxAndCrsOption = getBboxAndCrsOption(params, "boundingbox")
          (identifier, bboxAndCrsOption).mapN { case (id, (bbox, crsOption)) => (id, bbox, crsOption) }
        }

        /** gridBaseCRS is an optional param, in case it is missing we can always grab it from the data source. */
        val gridBaseCRS = params.validatedOptionalParam("gridbasecrs").andThen(_.traverse(CRSUtils.ogcToCRS))

        /** If the CRS is not provided, than it is in the CRS of the dataset. */
        val idAndBboxAndCrs: Validated[NEL[ParamError], (String, Vector[Double], CRS)] =
          idAndBboxAndCrsOption
            .andThen { case (id, bbox, crsOption) =>
              /** If the CRS wasn't in the boundingbox parameter, pull it out of the CRS field. */
              crsOption match {
                case Some(crsDesc) => CRSUtils.ogcToCRS(crsDesc).map(crs => (id, bbox, crs))
                case None =>
                  gridBaseCRS match {
                    case Valid(Some(crs)) => gridBaseCRS.map(_ => (id, bbox, crs))
                    case _                => Invalid(ParamError.MissingParam("BoundingBox CRS")).toValidatedNel
                  }
              }
            }

        val temporalSequenceOption = params.validatedOgcTimeSequence("timesequence")

        val format =
          params
            .validatedParam("format")
            .andThen { f =>
              OutputFormat.fromString(f) match {
                case Some(format) => Valid(format).toValidatedNel
                case None         => Invalid(UnsupportedFormatError(f)).toValidatedNel
              }
            }

        /** GridCS: default is "urn:ogc:def:cs:OGC:0.0:Grid2dSquareCS" */
        val gridCS = params
          .validatedOptionalParam[URI]("gridcs", s => Try(new URI(s)).toOption)
          .map(_.getOrElse(new URI("urn:ogc:def:cs:OGC:0.0:Grid2dSquareCS")))

        /**
         * GridType: default is "urn:ogc:def:method:WCS:1.1:2dSimpleGrid" (This GridType disallows rotation or skew relative to the GridBaseCRS –
         * therefore GridOffsets has only two numbers.)
         */
        val gridType = params
          .validatedOptionalParam[URI]("gridtype", s => Try(new URI(s)).toOption)
          .map(_.getOrElse(new URI("urn:ogc:def:method:WCS:1.1:2dSimpleGrid")))

        /** GridOrigin: default is "0,0" (KVP) or "0 0" (XML); it is the boundingBox corner. */
        val gridOrigin = params
          .validatedOptionalParam[(Double, Double)](
            "gridorigin",
            s =>
              Try {
                val List(fst, snd) = s.split(",").map(_.toDouble).toList
                (fst, snd)
              }.toOption
          )

        /** If not passed, than the original source resolution would be used. */
        val gridOffsets = params.validatedOptionalParam[(Double, Double)](
          "gridoffsets",
          s =>
            Try {

              /** In case 4 parameters would be passed, we care only about the first and the last only. */
              val list = s.split(",").map(_.toDouble).toList
              (list.head, list.last)
            }.toOption
        )

        (idAndBboxAndCrs, format, gridBaseCRS, gridCS, gridType, gridOrigin, gridOffsets, temporalSequenceOption).mapN {
          case ((id, bbox, crs), format, gridBaseCRS, gridCS, gridType, gridOrigin, gridOffsets, temporalSeqOpt) =>
            val extent = Extent(bbox(0), bbox(1), bbox(2), bbox(3))
            GetCoverageWcsParams(version, id, extent, temporalSeqOpt, format, gridBaseCRS, gridCS, gridType, gridOrigin, gridOffsets, crs, params)
        }
      }
  }
}
