package geotrellis.server.ogc.wcs.ops

import geotrellis.server.ogc.wcs._
import geotrellis.server.ogc.wcs.params.DescribeCoverageWcsParams

import scala.xml._

trait DescribeCoverage {
  def build(wcsModel: WcsModel, params: DescribeCoverageWcsParams): Elem
}
