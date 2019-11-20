package geotrellis.server.ogc.wcs.ops.version110

import geotrellis.server.ogc._
import geotrellis.server.ogc.wcs._
import geotrellis.server.ogc.wcs.ops.Common
import geotrellis.server.ogc.wcs.ops.{DescribeCoverage => DescribeCoverageBase}
import geotrellis.server.ogc.wcs.params.DescribeCoverageWcsParams

import geotrellis.proj4._
import geotrellis.raster._
import geotrellis.raster.reproject.ReprojectRasterExtent
import com.typesafe.scalalogging.LazyLogging

import scala.xml._

object DescribeCoverage extends DescribeCoverageBase with LazyLogging {
  private def createDescription(src: OgcSource) = {
    val identifier = src.name
    logger.info(s"Received v1.1.0 DescribeCoverage request for layer $identifier")

    val nativeCrs = src.nativeCrs.head
    val re = src.nativeRE
    val llre = src match {
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
    val llex = llre.extent
    val Dimensions(w, h) = llre.dimensions

    <CoverageDescription>
      <Identifier>{ identifier }</Identifier>
      <Description>Geotrellis layer</Description>
      <Domain>
        <SpatialDomain>
          { Common.boundingBox110(llre.extent, LatLng, "ows:WGS84BoundingBox") }
          <!-- Original projected extent: -->
          { Common.boundingBox110(re.extent, nativeCrs) }
          <ows:BoundingBox crs="urn:ogc:def:crs:OGC::imageCRS" dimensions="2">
            <ows:LowerCorner>0 0</ows:LowerCorner>
            <ows:UpperCorner>{ "%d %d".format(w, h) }</ows:UpperCorner>
          </ows:BoundingBox>
          <GridCRS>
            <GridBaseCRS>{ URN.unsafeFromCrs(nativeCrs) }</GridBaseCRS>
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
      <SupportedCRS>{ URN.unsafeFromCrs(LatLng) }</SupportedCRS>
      <SupportedFormat>image/tiff</SupportedFormat>
    </CoverageDescription>
  }

  def build(wcsModel: WcsModel, params: DescribeCoverageWcsParams): Elem = {
    logger.info("BUILDING COVERAGE", wcsModel, params)
    <CoverageDescriptions xmlns="http://www.opengis.net/wcs/1.1"
                          xmlns:ows="http://www.opengis.net/ows"
                          xmlns:xlink="http://www.w3.org/1999/xlink"
                          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                          xmlns:ogc="http://www.opengis.net/ogc"
                          xmlns:gml="http://www.opengis.net/gml"
                          version="1.1.0">
      { wcsModel.sources.map(createDescription(_)) }
    </CoverageDescriptions>
  }
}
