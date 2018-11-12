package geotrellis.server.wcs.ops.version100

import geotrellis.server.wcs.ops.MetadataCatalog
import geotrellis.server.wcs.ops.{DescribeCoverage => DescribeCoverageBase}
import geotrellis.server.wcs.params.DescribeCoverageWcsParams

import geotrellis.proj4._
import geotrellis.raster._
import geotrellis.raster.reproject.ReprojectRasterExtent
import geotrellis.spark._
import geotrellis.spark.io._
import geotrellis.spark.io.json._
import com.typesafe.scalalogging.LazyLogging

import scala.xml._

object DescribeCoverage extends DescribeCoverageBase {
  private def addDescriptions(catalog: MetadataCatalog)(identifier: String) = {
    logger.info(s"Received v1.0.0 DescribeCoverage request for layer $identifier")

    catalog(identifier)._2 match {
      case Some(metadata) =>
        val ex = metadata.boundsToExtent(metadata.gridBounds)
        val crs = metadata.crs
        val (w0, h0) = (metadata.gridBounds.width * metadata.tileCols, metadata.gridBounds.height * metadata.tileRows)
        val re = RasterExtent(ex, w0, h0)
        val llre = ReprojectRasterExtent(re, crs, LatLng)
        val llex = llre.extent
        val (w, h) = llre.dimensions

        <CoverageOffering>
          <name>{ identifier }</name>
          <label>{ identifier }</label>
          <Description>Geotrellis layer</Description>
          <lonLatEnvelope srsName="WGS84(DD)">
            <gml:pos>{llex.xmin} {llex.ymin}</gml:pos>
            <gml:pos>{llex.xmax} {llex.ymax}</gml:pos>
          </lonLatEnvelope>
          <domainSet>
            <spatialDomain>
              <gml:Envelope srsName="EPSG:4326">
                <gml:pos>{llex.xmin} {llex.ymin}</gml:pos>
                <gml:pos>{llex.xmax} {llex.ymax}</gml:pos>
              </gml:Envelope>
              <gml:RectifiedGrid>
                <gml:limits>
                  <gml:GridEnvelope>
                    <gml:low>{ "%d %d".format(0, 0) }</gml:low>
                    <gml:high>{ "%d %d".format(w - 1, h - 1) }</gml:high>
                  </gml:GridEnvelope>
                </gml:limits>
                <gml:axisName>x</gml:axisName>
                <gml:axisName>y</gml:axisName>
                <gml:origin>
                  <gml:pos>{ ({ loc: (Double, Double) => "%f %f".format(loc._1, loc._2)})(llre.gridToMap(0, h - 1)) }</gml:pos>
                </gml:origin>
                <gml:offsetVector>
                  { "%f 0.0".format(llre.cellwidth) }
                </gml:offsetVector>
                <gml:offsetVector>
                  { "0.0 %f".format(-llre.cellheight) }
                </gml:offsetVector>
              </gml:RectifiedGrid>
            </spatialDomain>
          </domainSet>
          <rangeSet>
            <RangeSet>
              <name>Band</name>
              <label>Geotrellis Layer</label>
            </RangeSet>
          </rangeSet>
          <supportedCRSs>
            <requestResponseCRSs>EPSG:4326</requestResponseCRSs>
          </supportedCRSs>
          <supportedFormats>
            <formats>GeoTIFF</formats>
          </supportedFormats>
        </CoverageOffering>

      case None =>
        val comment = <!-- -->
        comment.copy(commentText = s"No metadata available for $identifier")
    }
  }

  def build(metadata: MetadataCatalog, params: DescribeCoverageWcsParams): Elem = {
    logger.info("BUILDING COVERAGE", metadata, params)
    <CoverageDescription xmlns="http://www.opengis.net/wcs"
                         xmlns:xlink="http://www.w3.org/1999/xlink"
                         xmlns:gml="http://www.opengis.net/gml"
                         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                         xsi:schemaLocation="http://www.opengis.net/wcs http://schemas.opengis.net/wcs/1.0.0/describeCoverage.xsd"
                         version="1.0.0">
      { params.identifiers.map(addDescriptions(metadata)(_)) }
    </CoverageDescription>
  }
}
