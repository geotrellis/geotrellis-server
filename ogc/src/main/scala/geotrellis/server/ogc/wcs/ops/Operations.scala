package geotrellis.server.ogc.wcs.ops

import geotrellis.server.ogc._
import geotrellis.server.ogc.wcs.params._

import scala.xml._

object Operations {
  def getCapabilities(metadata: ows.ServiceMetadata, requestURL: String, rsm: RasterSourcesModel, params: GetCapabilitiesWcsParams): Elem = {
    if (params.version < "1.1")
      version100.GetCapabilities.build(metadata, requestURL, rsm, params)
    else
      version110.GetCapabilities.build(metadata, requestURL, rsm, params)
  }

  def describeCoverage(rsm: RasterSourcesModel, params: DescribeCoverageWcsParams): Elem = {
    if (params.version < "1.1")
      version100.DescribeCoverage.build(rsm: RasterSourcesModel, params: DescribeCoverageWcsParams)
    else
      version110.DescribeCoverage.build(rsm: RasterSourcesModel, params: DescribeCoverageWcsParams)
  }
}
