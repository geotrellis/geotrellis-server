package geotrellis.server.http4s.wcs.ops

import geotrellis.server.http4s.wcs.WcsService
import geotrellis.server.http4s.wcs.params.GetCapabilitiesWcsParams
import geotrellis.spark._
import geotrellis.spark.io._
import com.typesafe.scalalogging.LazyLogging

import scala.util.Try
import scala.xml._

object GetCapabilities extends LazyLogging {
  // Cribbed from https://github.com/ngageoint/mrgeo/blob/master/mrgeo-services/mrgeo-services-wcs/src/main/java/org/mrgeo/services/wcs/WcsCapabilities.java

  private def makeElement100(requestURL: String) = {
    <HTTP>
      <Get>
        <OnlineResource xmlns:xlink="http://www.w3.org/1999/xlink" xlink:type="simple" xlink:href={ requestURL } />
      </Get>
      <Post>
        <OnlineResource xmlns:xlink="http://www.w3.org/1999/xlink" xlink:type="simple" xlink:href={ requestURL } />
      </Post>
    </HTTP>
  }

  private def makeElement110(requestURL: String, operation: String) = {
    <ows:Operation name={ operation }>
      <ows:DCP>
        <ows:HTTP>
          <ows:Get xlink:href={ requestURL }></ows:Get>
          <ows:Post xlink:href={ requestURL }></ows:Post>
        </ows:HTTP>
      </ows:DCP>
    </ows:Operation>
  }

  private def addLayers100(metadata: WcsService.MetadataCatalog) = {
    metadata.map { case (identifier, (zooms, maybeMetadata)) => {
      logger.info(s"Adding v1.0.0 tag for $identifier")
      maybeMetadata match {
        case Some(metadata) =>
          val crs = metadata.crs
          val ex = metadata.extent
          <CoverageOfferingBrief>
            <name>{ identifier }</name>
          </CoverageOfferingBrief>
        case None =>
          val comment = <!--  -->
          comment.copy(commentText = s"Loading of $identifier failed")
      }
    }}
  }

  private def addLayers110(metadata: WcsService.MetadataCatalog) = {
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

  def build(requestURL: String, metadata: WcsService.MetadataCatalog, params: GetCapabilitiesWcsParams): Elem = {
    if (params.version < "1.1.0") {
      <WCS_Capabilities xmlns="http://www.opengis.net/wcs"
                        xmlns:xlink="http://www.w3.org/1999/xlink"
                        xmlns:gml="http://www.opengis.net/gml"
                        xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                        xsi:schemaLocation={"http://www.opengis.net/wcs http://schemas.opengeospatial.net/wcs/" + params.version + "/wcsCapabilities.xsd"}
                        version={params.version}>
        <Service>
          <name>OGC:WC</name>
          <description>Geotrellis Web Coverage Service</description>
          <label>Geotrellis Web Coverage Service</label>
          <fees>NONE</fees>
          <accessConstraints>NONE</accessConstraints>
        </Service>
        <Capability>
          <Request>
            <GetCapabilities>
              <DCPType>
                { makeElement100(requestURL) }
              </DCPType>
            </GetCapabilities>
            <DescribeCoverage>
              <DCPType>
                { makeElement100(requestURL) }
              </DCPType>
            </DescribeCoverage>
            <GetCoverage>
              <DCPType>
                { makeElement100(requestURL) }
              </DCPType>
            </GetCoverage>
          </Request>
          <Exception>
            <Format>application/vnd.ogc.se_xml</Format>
          </Exception>
        </Capability>
        <ContentMetadata>
          { addLayers100(metadata) }
        </ContentMetadata>
      </WCS_Capabilities>
    } else {
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
          { makeElement110(requestURL, "GetCapabilities") }
          { makeElement110(requestURL, "DescribeCoverage") }
          { makeElement110(requestURL, "GetCoverage") }
        </ows:OperationsMetadata>
        <ows:ServiceProvider>
        </ows:ServiceProvider>
        <wcs:Contents>
          { addLayers110(metadata) }
        </wcs:Contents>
      </wcs:Capabilities>
    }
  }
}
