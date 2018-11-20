package geotrellis.server.wcs.ops

import geotrellis.server.wcs.params._

import scala.xml._

object Operations {
  def getCapabilities(requestURL: String, metadata: MetadataCatalog, params: GetCapabilitiesWcsParams): Elem = {
    if (params.version < "1.1")
      version100.GetCapabilities.build(requestURL, metadata, params)
    else
      version110.GetCapabilities.build(requestURL, metadata, params)
  }

  def describeCoverage(metadata: MetadataCatalog, params: DescribeCoverageWcsParams): Elem = {
    if (params.version < "1.1")
      version100.DescribeCoverage.build(metadata: MetadataCatalog, params: DescribeCoverageWcsParams)
    else
      version110.DescribeCoverage.build(metadata: MetadataCatalog, params: DescribeCoverageWcsParams)
  }
}
