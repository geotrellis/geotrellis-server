package geotrellis.server.ogc.wcs.params

import geotrellis.server.ogc.wcs._
import geotrellis.server.ogc.OutputFormat
import geotrellis.raster.{CellSize, GridExtent}
import geotrellis.proj4.CRS
import geotrellis.vector.Extent

import cats.syntax.apply._
import cats.data.{NonEmptyList, Validated, ValidatedNel}
import cats.data.Validated._

import scala.util.Try
import java.net.URI

abstract sealed class WcsParams {
  val version: String
}

case class GetCapabilitiesWcsParams(version: String) extends WcsParams

case class DescribeCoverageWcsParams(version: String, identifiers: Seq[String]) extends WcsParams

/**
 * “EPSG:4326” or “WGS84” use the latitude first, longitude second axis order.
 * According to the WCS spec for 1.1, some CRS have inverted axis
 * box:
 *  1.0.0: minx,miny,maxx,maxy
 *  1.1.0, 1.1.2: OGC 07-067r5 (WCS 1.1.2) referes to OGC 06-121r3 which says
 *  "The number of axes included, and the order of these axes, shall be as specified
 *  by the referenced CRS." That means inverted for geographic.
 *
 * Reference to QGIS: https://github.com/qgis/QGIS/blob/final-3_10_2/src/providers/wcs/qgswcsprovider.cpp#L674
 * Parameters descriptions can be also found here: https://mapserver.org/ogc/wcs_server.html
 */
case class GetCoverageWcsParams(
  version: String,
  identifier: String,
  boundingBox: Extent,
  format: OutputFormat,
  gridBaseCRS: CRS,
  gridCS: URI,
  gridType: URI,
  // GridOrigin is BBOX minx, maxy // swapped in case of a geographic projection
  gridOrigin: (Double, Double),
  // GridOffsets is xres, yres // swapped in case of a geographic projection
  gridOffsets: (Double, Double),
  crs: CRS
) extends WcsParams {
  val changeXY: Boolean = crs.isGeographic

  def cellSize: CellSize =
    if(changeXY) CellSize(-gridOffsets._1, gridOffsets._2)
    else CellSize(gridOffsets._1, -gridOffsets._2)

  // shrink the extent to border cells centers by half cell size
  def extent: Extent =
    if(changeXY)
      Extent(
        boundingBox.xmin,
        gridOrigin._2,
        gridOrigin._1,
        boundingBox.ymax
      ).buffer(cellSize.width / 2, cellSize.height / 2).swapXY
    else
      Extent(
        gridOrigin._1,
        boundingBox.ymin,
        boundingBox.xmax,
        gridOrigin._2
      ).buffer(cellSize.width / 2, cellSize.height / 2)

  def gridExtent: GridExtent[Long] = GridExtent[Long](extent, cellSize)
}

object WcsParams {
  /** Defines valid request types, and the WcsParams to build from them. */
  private val requestMap: Map[String, ParamMap => ValidatedNel[WcsParamsError, WcsParams]] =
    Map(
      "getcapabilities"  -> GetCapabilitiesWcsParams.build,
      "describecoverage" -> DescribeCoverageWcsParams.build,
      "getcoverage"      -> GetCoverageWcsParams.build
    )

  private val validRequests = requestMap.keys.toSet

  def apply(queryParams: Map[String, Seq[String]]): ValidatedNel[WcsParamsError, WcsParams] = {
    val params = ParamMap(queryParams)

    val serviceParam = params.validatedParam("service", validValues=Set("wcs"))
    val requestParam = params.validatedParam("request", validValues=validRequests)
    val firstStageValidation = (serviceParam, requestParam).mapN { case (_, b) => b }

    firstStageValidation
      // Further validation and building based on request type.
      .andThen { request => requestMap(request)(params) }
  }
}

object GetCapabilitiesWcsParams {
  def build(params: ParamMap): ValidatedNel[WcsParamsError, WcsParams] = {
    val versionParam = params.validatedVersion
    versionParam.map { version: String => GetCapabilitiesWcsParams(version) }
  }
}

object DescribeCoverageWcsParams {
  def build(params: ParamMap): ValidatedNel[WcsParamsError, WcsParams] = {
    val versionParam =
      params.validatedVersion

    versionParam
      .andThen { version: String =>
        params
          .validatedParam("identifiers")
          .map(_.split(",").toSeq)
          .map { ids => (version, ids) }
      }
      .map { case (version, identifiers) => DescribeCoverageWcsParams(version, identifiers) }
  }
}

object GetCoverageWcsParams {
  private def getBboxAndCrsOption(params: ParamMap, field: String): ValidatedNel[WcsParamsError, (Vector[Double], Option[String])] =
    params.validatedParam[(Vector[Double], Option[String])](field, { bboxStr =>
      // Usually the CRS is the 5th element in the bbox param.
      try {
        val v = bboxStr.split(",").toVector
        if(v.length == 4) Some((v.map(_.toDouble), None))
        else if(v.length == 5) Some((v.take(4).map(_.toDouble), Some(v.last)))
        else None
      } catch {
        case _: Throwable => None
      }
    })

  private[params] def build(params: ParamMap): ValidatedNel[WcsParamsError, WcsParams] = {
    val versionParam =
      params.validatedVersion

    versionParam
      .andThen { version: String =>
        // Collected the bbox, id, and possibly the CRS in one shot.
        // This is because the boundingbox param could contain the CRS as the 5th element.
        val idAndBboxAndCrsOption = {
          val identifier =
            params.validatedParam("identifier")

          val bboxAndCrsOption =
            getBboxAndCrsOption(params, "boundingbox")

          (identifier, bboxAndCrsOption).mapN { case (id, (bbox, crsOption)) =>
            (id, bbox, crsOption)
          }
        }

        val gridBaseCRS = params.validatedParam("gridbasecrs").andThen(CRSUtils.ogcToCRS)

        // Transform the OGC urn CRS code into a CRS.
        val idAndBboxAndCrs: Validated[NonEmptyList[WcsParamsError], (String, Vector[Double], CRS)] =
          idAndBboxAndCrsOption
            .andThen { case (id, bbox, crsOption) =>
              // If the CRS wasn't in the boundingbox parameter, pull it out of the CRS field.
              crsOption match {
                case Some(crsDesc) => CRSUtils.ogcToCRS(crsDesc).map { crs => (id, bbox, crs) }
                case None => gridBaseCRS.map { crs => (id, bbox, crs) }
              }
            }

        val format =
          params.validatedParam("format")
            .andThen { f =>
              OutputFormat.fromString(f) match {
                case Some(format) => Valid(format).toValidatedNel
                case None =>
                  Invalid(UnsupportedFormatError(f)).toValidatedNel
                }
            }

        val gridCS = params.validatedParam[URI]("gridcs", { s => Try(new URI(s)).toOption })
        val gridType = params.validatedParam[URI]("gridtype", { s => Try(new URI(s)).toOption })
        val gridOrigin = params.validatedParam[(Double, Double)]("gridorigin", { s =>
          Try {
            val List(fst, snd) = s.split(",").map(_.toDouble).toList
            (fst, snd)
          }.toOption
        })

        val gridOffsets = params.validatedParam[(Double, Double)]("gridoffsets", { s =>
          Try {
            // in case 4 parameters would be passed, we care only about the first and the last only
            val list = s.split(",").map(_.toDouble).toList
            (list.head, list.last)
          }.toOption
        })

        (idAndBboxAndCrs, format, gridBaseCRS, gridCS, gridType, gridOrigin, gridOffsets).mapN {
          case ((id, bbox, crs), format, gridBaseCRS, gridCS, gridType, gridOrigin, gridOffsets) =>
          val extent = Extent(bbox(0), bbox(1), bbox(2), bbox(3))
          GetCoverageWcsParams(version, id, extent, format, gridBaseCRS, gridCS, gridType, gridOrigin, gridOffsets, crs)
        }
      }
  }
}
