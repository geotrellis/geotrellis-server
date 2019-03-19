package geotrellis.server.ogc.wcs.ops

import geotrellis.server.ogc._
import geotrellis.server.ogc.wcs._
import geotrellis.server.ogc.wcs.params._

import scala.xml._

object Operations {
  def getCapabilities(requestURL: String, wcsModel: WcsModel, params: GetCapabilitiesWcsParams): Elem = {
    if (params.version < "1.1")
      version100.GetCapabilities.build(requestURL, wcsModel, params)
    else
      version110.GetCapabilities.build(requestURL, wcsModel, params)
  }

  def describeCoverage(wcsModel: WcsModel, params: DescribeCoverageWcsParams): Elem = {
    if (params.version < "1.1")
      version100.DescribeCoverage.build(wcsModel: WcsModel, params: DescribeCoverageWcsParams)
    else
      version110.DescribeCoverage.build(wcsModel: WcsModel, params: DescribeCoverageWcsParams)
  }
}
