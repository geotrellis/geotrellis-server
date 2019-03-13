package geotrellis.server.ogc.wcs.ops.version100

import geotrellis.server.ogc._
import geotrellis.server.ogc.wcs.ops.{GetCapabilities => GetCapabilitiesBase}
import geotrellis.server.ogc.wcs.params.GetCapabilitiesWcsParams

import geotrellis.spark._
import geotrellis.spark.io._
import com.typesafe.scalalogging.LazyLogging

import scala.util.Try
import scala.xml._

object GetCapabilities extends GetCapabilitiesBase with LazyLogging {
  // Cribbed from https://github.com/ngageoint/mrgeo/blob/master/mrgeo-services/mrgeo-services-wcs/src/main/java/org/mrgeo/services/wcs/WcsCapabilities.java

  private def makeElement(requestURL: String) = {
    <HTTP>
      <Get>
        <OnlineResource xmlns:xlink="http://www.w3.org/1999/xlink" xlink:type="simple" xlink:href={ requestURL } />
      </Get>
      <Post>
        <OnlineResource xmlns:xlink="http://www.w3.org/1999/xlink" xlink:type="simple" xlink:href={ requestURL } />
      </Post>
    </HTTP>
  }

  private def addLayers(rsm: RasterSourcesModel) = {
    rsm.sourceLookup.map { case (identifier, src) => {
      logger.info(s"Adding v1.0.0 tag for $identifier")
        val crs = src.nativeCrs
        val ex = src.nativeExtent
        <CoverageOfferingBrief>
          <name>{ identifier }</name>
        </CoverageOfferingBrief>
    }}
  }

  def build(metadata: ows.ServiceMetadata, requestURL: String, rsm: RasterSourcesModel, params: GetCapabilitiesWcsParams): Elem = {
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
              { makeElement(requestURL) }
            </DCPType>
          </GetCapabilities>
          <DescribeCoverage>
            <DCPType>
              { makeElement(requestURL) }
            </DCPType>
          </DescribeCoverage>
          <GetCoverage>
            <DCPType>
              { makeElement(requestURL) }
            </DCPType>
          </GetCoverage>
        </Request>
        <Exception>
          <Format>application/vnd.ogc.se_xml</Format>
        </Exception>
      </Capability>
      <ContentMetadata>
        { addLayers(rsm) }
      </ContentMetadata>
    </WCS_Capabilities>
  }
}
