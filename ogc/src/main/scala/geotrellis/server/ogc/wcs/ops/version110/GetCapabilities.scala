package geotrellis.server.ogc.wcs.ops.version110

import geotrellis.server.ogc._
import geotrellis.server.ogc.wcs.ops.Common
import geotrellis.server.ogc.wcs.ops.{GetCapabilities => GetCapabilitiesBase}
import geotrellis.server.ogc.wcs.params.GetCapabilitiesWcsParams

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

  private def addLayers(rsm: RasterSourcesModel) = {
    rsm.sourceLookup.map { case (identifier, src) => {
      logger.info(s"Adding v1.1.0 tag for $identifier")
      val crs = src.nativeCrs.head
      val ex = src.nativeExtent
      <wcs:CoverageSummary>
        <wcs:Identifier>{ identifier }</wcs:Identifier>
        { Common.boundingBox110(ex, crs) }
        {
          if (crs.epsgCode.isDefined) {
            <SupportedCRS>{ URN.unsafeFromCrs(crs) }</SupportedCRS>
          }
        }
        <SupportedFormat>image/geotiff</SupportedFormat>
        <SupportedFormat>image/geotif</SupportedFormat>
        <SupportedFormat>image/tiff</SupportedFormat>
        <SupportedFormat>image/tif</SupportedFormat>
      </wcs:CoverageSummary>
    }}
  }

  def build(metadata: ows.ServiceMetadata, requestURL: String, rsm: RasterSourcesModel, params: GetCapabilitiesWcsParams): Elem = {
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
        <ows:Title>{ metadata.identification.title }</ows:Title>
        <ows:Abstract>{ metadata.identification.description }</ows:Abstract>
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
        <ows:ProviderName></ows:ProviderName>
        { metadata.provider.site
          .map({ site => <ows:ProviderSite>{ site }</ows:ProviderSite> })
          .getOrElse(NodeSeq.Empty)
        }
      </ows:ServiceProvider>
      <wcs:Contents>
        { addLayers(rsm) }
      </wcs:Contents>
    </wcs:Capabilities>
  }
}
