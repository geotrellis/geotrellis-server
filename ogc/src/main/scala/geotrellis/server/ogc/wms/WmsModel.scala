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

import java.time.ZonedDateTime

import cats.implicits._
import geotrellis.server.ogc._
import geotrellis.server.ogc.style._
import geotrellis.server.ogc.wms.WmsParams.GetMap
import jp.ne.opt.chronoscala.Imports._


/** This class holds all the information necessary to construct a response to a WMS request */
case class WmsModel(
  serviceMeta: opengis.wms.Service,
  parentLayerMeta: WmsParentLayerMeta,
  sources: OgcSourceRepository
) {

  private lazy val times: List[ZonedDateTime] = sources.store.flatMap(_.time)

  lazy val temporalRange: Option[OgcTimeInterval] = {
    times match {
      case Nil => None
      case head :: Nil => OgcTimeInterval(head).some
      case list => OgcTimeInterval(list.min, Some(list.max), None).some
    }
  }

  /** Take a specific request for a map and combine it with the relevant [[OgcSource]]
   *  to produce an [[OgcLayer]]
   */
  def getLayer(params: GetMap): List[OgcLayer] = {
    val p = temporalRange match {
      // Add default time to GetMap params if this is a temporal layer and the user didn't request a specific time
      case Some(timeInterval) if params.time.isEmpty =>
        params.copy(time = OgcTimeInterval(timeInterval.start).some)
      case _ => params
    }
    for {
      supportedCrs <- parentLayerMeta.supportedProjections.find(_ == p.crs).toList
      source       <- sources.find(p.toQuery)
    } yield {
      val styleName: Option[String] =
        p.styles
          .headOption
          .filterNot(_.isEmpty)
          .orElse(source.defaultStyle)
      val style: Option[OgcStyle] =
        styleName.flatMap { name => source.styles.find(_.name == name) }
      source match {
        case MapAlgebraSource(name, title, rasterSources, algebra, defaultStyle, styles) =>
          val simpleLayers = rasterSources.mapValues { rs =>
            SimpleOgcLayer(name, title, supportedCrs, rs, style)
          }
          MapAlgebraOgcLayer(name, title, supportedCrs, simpleLayers, algebra, style)
        case SimpleSource(name, title, rasterSource, defaultStyle, styles) =>
          SimpleOgcLayer(name, title, supportedCrs, rasterSource, style)
      }
    }
  }
}
