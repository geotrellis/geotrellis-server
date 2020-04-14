/*
 * Copyright 2020 Azavea
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package geotrellis.server.ogc.wms

import geotrellis.server.ogc._
import geotrellis.server.ogc.params.ParamMap
import geotrellis.server.ogc.style._
import geotrellis.server.ogc.wms.WmsParams.GetMap
import geotrellis.server.ogc.utils._

import com.azavea.maml.ast.Expression

/** This class holds all the information necessary to construct a response to a WMS request */
case class WmsModel(
  serviceMeta: opengis.wms.Service,
  parentLayerMeta: WmsParentLayerMeta,
  sources: OgcSourceRepository,
  extendedParametersBinding: Option[ParamMap => Option[Expression => Expression]] = None
) {

  def timeInterval: Option[OgcTimeInterval] =
    sources.store.flatMap(_.timeInterval).reduceOption(_ combine _)

  /** Take a specific request for a map and combine it with the relevant [[OgcSource]]
    *  to produce an [[OgcLayer]]
    */
  def getLayer(p: GetMap): List[OgcLayer] = {
    parentLayerMeta.supportedProjections
      .find(_ == p.crs)
      .fold[List[OgcLayer]](List()) { supportedCrs =>
        sources.find(p.toQuery).map { source =>
          val styleName: Option[String] = p.styles.headOption.filterNot(_.isEmpty)
            .orElse(source.defaultStyle)
          val style: Option[OgcStyle] = styleName.flatMap { name =>
            source.styles.find(_.name == name)
          }
          source match {
            case MapAlgebraSource(name, title, rasterSources, algebra, _, _, resampleMethod, overviewStrategy) =>
              val simpleLayers = rasterSources.mapValues { rs =>
                SimpleOgcLayer(name, title, supportedCrs, rs, style, resampleMethod, overviewStrategy)
              }
              val extendedParameters = extendedParametersBinding.flatMap(_.apply(p.params))
              MapAlgebraOgcLayer(name, title, supportedCrs, simpleLayers, algebra.bindExtendedParameters(extendedParameters), style, resampleMethod, overviewStrategy)
            case SimpleSource(name, title, rasterSource, _, _, resampleMethod, overviewStrategy) =>
              SimpleOgcLayer(name, title, supportedCrs, rasterSource, style, resampleMethod, overviewStrategy)
            case gts @ GeoTrellisOgcSource(name, title, _, _, _, resampleMethod, overviewStrategy, _) =>
              val rasterSource = p.time.fold(gts.source)(gts.sourceForTime)
              SimpleOgcLayer(name, title, supportedCrs, rasterSource, style, resampleMethod, overviewStrategy)
          }
        }
      }
  }
}
