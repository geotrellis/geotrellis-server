package geotrellis.server.core.wcs.ops

import geotrellis.server.core.wcs.params.DescribeCoverageWcsParams

import geotrellis.proj4._
import geotrellis.raster._
import geotrellis.raster.reproject.ReprojectRasterExtent
import geotrellis.spark._
import geotrellis.spark.io._
import geotrellis.spark.io.json._
import com.typesafe.scalalogging.LazyLogging

import scala.xml._

object DescribeCoverage extends LazyLogging {
  private def addDescriptions110(catalog: MetadataCatalog)(identifier: String) = {
    logger.info(s"Received v1.1.0 DescribeCoverage request for layer $identifier")

    catalog(identifier)._2 match {
      case Some(metadata) =>
        val ex = metadata.boundsToExtent(metadata.gridBounds)
        val crs = metadata.crs
        val (w, h) = (metadata.gridBounds.width * metadata.tileCols, metadata.gridBounds.height * metadata.tileRows)
        val re = RasterExtent(ex, w, h)
        val llre = ReprojectRasterExtent(re, crs, LatLng)
        val llex = llre.extent

        <CoverageDescription>
          <Identifier>{ identifier }</Identifier>
          <Description>Geotrellis layer</Description>
          <Domain>
            <SpatialDomain>
              { Common.boundingBox110(ex.reproject(crs, LatLng), LatLng, "ows:WGS84BoundingBox") }
              <!-- Orginal projected extent: -->
              { Common.boundingBox110(ex, crs) }
              <ows:BoundingBox crs="urn:ogc:def:crs:OGC::imageCRS" dimensions="2">
                <ows:LowerCorner>0 0</ows:LowerCorner>
                <ows:UpperCorner>{ "%d %d".format(w, h) }</ows:UpperCorner>
              </ows:BoundingBox>
              <GridCRS>
                <GridBaseCRS>{ Common.crsToUrnString(crs) }</GridBaseCRS>
                <GridType>urn:ogc:def:method:WCS:1.1:2dGridIn2dCrs</GridType>
                <GridOrigin>{ ({ loc: (Double, Double) => "%f %f".format(loc._1, loc._2)})(re.gridToMap(0, h - 1)) }</GridOrigin>
                <GridOffsets>{ "%f %f".format(re.cellwidth, -re.cellheight) }</GridOffsets>
                <GridCS>urn:ogc:def:cs:OGC:0.0:Grid2dSquareCS</GridCS>
              </GridCRS>
            </SpatialDomain>
          </Domain>
          <Range>
            <Field>
              <Identifier>contents</Identifier>
              <Definition>
                <AnyValue />
              </Definition>
              <InterpolationMethods>
                <DefaultMethod>nearest neighbor</DefaultMethod>
                <OtherMethod>bilinear</OtherMethod>
              </InterpolationMethods>
            </Field>
          </Range>
          <SupportedCRS>{ Common.crsToUrnString(LatLng) }</SupportedCRS>
          <SupportedFormat>image/tiff</SupportedFormat>
        </CoverageDescription>

      case None =>
        val comment = <!-- -->
        comment.copy(commentText = s"No metadata available for $identifier")
    }
  }

  private def addDescriptions100(catalog: MetadataCatalog)(identifier: String) = {
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
    if (params.version < "1.1") {
      <CoverageDescription xmlns="http://www.opengis.net/wcs"
                           xmlns:xlink="http://www.w3.org/1999/xlink"
                           xmlns:gml="http://www.opengis.net/gml"
                           xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                           xsi:schemaLocation="http://www.opengis.net/wcs http://schemas.opengis.net/wcs/1.0.0/describeCoverage.xsd"
                           version="1.0.0">
        { params.identifiers.map(addDescriptions100(metadata)(_)) }
      </CoverageDescription>
    } else {
      <CoverageDescriptions xmlns="http://www.opengis.net/wcs/1.1"
                            xmlns:ows="http://www.opengis.net/ows"
                            xmlns:xlink="http://www.w3.org/1999/xlink"
                            xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                            xmlns:ogc="http://www.opengis.net/ogc"
                            xmlns:gml="http://www.opengis.net/gml"
                            version="1.1.0">
        { params.identifiers.map(addDescriptions110(metadata)(_)) }
      </CoverageDescriptions>
    }
  }
}
