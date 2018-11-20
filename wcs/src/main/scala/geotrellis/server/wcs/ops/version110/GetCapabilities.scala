package geotrellis.server.wcs.ops.version110

import geotrellis.server.wcs.ops.{Common, MetadataCatalog}
import geotrellis.server.wcs.ops.{GetCapabilities => GetCapabilitiesBase}
import geotrellis.server.wcs.params.GetCapabilitiesWcsParams

import geotrellis.spark._
import geotrellis.spark.io._
import com.typesafe.scalalogging.LazyLogging

import scala.util.Try
import scala.xml._

object GetCapabilities extends GetCapabilitiesBase with LazyLogging {
  // Cribbed from https://github.com/ngageoint/mrgeo/blob/master/mrgeo-services/mrgeo-services-wcs/src/main/java/org/mrgeo/services/wcs/WcsCapabilities.java

  private def makeElement(requestURL: String, operation: String) = {
    <ows:Operation name={ operation }>
      <ows:DCP>
        <ows:HTTP>
          <ows:Get xlink:href={ requestURL }></ows:Get>
          <ows:Post xlink:href={ requestURL }></ows:Post>
        </ows:HTTP>
      </ows:DCP>
    </ows:Operation>
  }

  private def addLayers(metadata: MetadataCatalog) = {
    metadata.map { case (identifier, (zooms, maybeMetadata)) => {
      logger.info(s"Adding v1.1.0 tag for $identifier")
      maybeMetadata match {
        case Some(metadata) =>
          val crs = metadata.crs
          val ex = metadata.extent
          <wcs:CoverageSummary>
            <wcs:Identifier>{ identifier }</wcs:Identifier>
            { Common.boundingBox110(ex, crs) }
            {
              if (crs.epsgCode.isDefined) {
                <SupportedCRS>urn:ogs:def:crs:EPSG::{ crs.epsgCode.get.toString }</SupportedCRS>
              }
            }
            <SupportedFormat>image/geotiff</SupportedFormat>
            <SupportedFormat>image/geotif</SupportedFormat>
            <SupportedFormat>image/tiff</SupportedFormat>
            <SupportedFormat>image/tif</SupportedFormat>
          </wcs:CoverageSummary>
        case None =>
          val comment = <!--  -->
          comment.copy(commentText = s"Loading of $identifier failed")
      }
    }}
  }

  def build(requestURL: String, metadata: MetadataCatalog, params: GetCapabilitiesWcsParams): Elem = {
    // Pulled example template from http://nsidc.org/cgi-bin/atlas_north?service=WCS&request=GetCapabilities&version=1.1.1
    val version = params.version.split('.')
    <wcs:Capabilities xmlns:wcs={"http://www.opengis.net/wcs/" + version(0) + "." + version(1)}
                      xmlns:xlink="http://www.w3.org/1999/xlink"
                      xmlns:ogc="http://www.opengis.net/ogc"
                      xmlns:ows="http://www.opengis.net/ows"
                      xmlns:gml="http://www.opengis.net/gml"
                      xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                      version={params.version}>
      <ows:ServiceIdentification>
        <ows:Title>GeoTrellis Web Coverage Service</ows:Title>
        <ows:ServiceType>OGS WCS</ows:ServiceType>
        <ows:ServiceTypeVersion>1.1.0</ows:ServiceTypeVersion>
        <ows:Fees>NONE</ows:Fees>
        <ows:AccessConstraints>NONE</ows:AccessConstraints>
      </ows:ServiceIdentification>
      <ows:OperationsMetadata>
        { makeElement(requestURL, "GetCapabilities") }
        { makeElement(requestURL, "DescribeCoverage") }
        { makeElement(requestURL, "GetCoverage") }
      </ows:OperationsMetadata>
      <ows:ServiceProvider>
      </ows:ServiceProvider>
      <wcs:Contents>
        { addLayers(metadata) }
      </wcs:Contents>
    </wcs:Capabilities>
  }
}
