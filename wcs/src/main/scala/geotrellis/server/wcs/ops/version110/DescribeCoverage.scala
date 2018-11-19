package geotrellis.server.wcs.ops.version110

import geotrellis.server.wcs.ops.{Common, MetadataCatalog}
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

object DescribeCoverage extends DescribeCoverageBase with LazyLogging {
  private def addDescriptions(catalog: MetadataCatalog)(identifier: String) = {
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

  def build(metadata: MetadataCatalog, params: DescribeCoverageWcsParams): Elem = {
    logger.info("BUILDING COVERAGE", metadata, params)
    <CoverageDescriptions xmlns="http://www.opengis.net/wcs/1.1"
                          xmlns:ows="http://www.opengis.net/ows"
                          xmlns:xlink="http://www.w3.org/1999/xlink"
                          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                          xmlns:ogc="http://www.opengis.net/ogc"
                          xmlns:gml="http://www.opengis.net/gml"
                          version="1.1.0">
      { params.identifiers.map(addDescriptions(metadata)(_)) }
    </CoverageDescriptions>
  }
}
