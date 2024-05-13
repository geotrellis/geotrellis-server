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
import geotrellis.server.ogc.wms.WmsParams.{GetFeatureInfoExtendedParams, GetMapParams}
import geotrellis.server.ogc.utils._
import geotrellis.store.query.{Query, RepositoryM}
import geotrellis.proj4.CRS
import com.azavea.maml.ast.Expression
import cats.Monad
import cats.syntax.functor._
import cats.syntax.applicative._
import cats.syntax.semigroup._
import cats.syntax.option._

/**
 * This class holds all the information necessary to construct a response to a WMS request
 */
case class WmsModel[F[_]: Monad](
  serviceMeta: opengis.wms.Service,
  parentLayerMeta: WmsParentLayerMeta,
  sources: RepositoryM[F, List, OgcSource],
  extendedParametersBinding: Option[ParamMap => Option[Expression => Expression]] = None
) {

  def time: F[OgcTime] = sources.store.map(_.map(_.time)).map(_.reduce(_ |+| _))

  private def getLayer(query: Query, time: OgcTime, supportedCrs: CRS, styles: List[String], params: Option[ParamMap]): F[List[OgcLayer]] =
    sources.find(query).map { sources =>
      sources.map { source =>
        val styleName: Option[String] = styles.headOption.filterNot(_.isEmpty).orElse(source.defaultStyle)
        val style: Option[OgcStyle] = styleName.flatMap { name =>
          source.styles.find(_.name == name)
        }
        source match {
          case rs: RasterOgcSource => rs.toLayer(supportedCrs, style, time :: Nil)
          case mas: MapAlgebraSource =>
            val (name, title, algebra, resampleMethod, overviewStrategy) =
              (mas.name, mas.title, mas.algebra, mas.resampleMethod, mas.overviewStrategy)
            val simpleLayers = mas.sources.map { case (key, rs) =>
              key -> SimpleOgcLayer(name, title, supportedCrs, rs, style, resampleMethod, overviewStrategy)
            }
            val extendedParameters = params.flatMap(p => extendedParametersBinding.flatMap(_.apply(p)))
            MapAlgebraOgcLayer(
              name,
              title,
              supportedCrs,
              simpleLayers,
              algebra.bindExtendedParameters(extendedParameters),
              style,
              resampleMethod,
              overviewStrategy,
              mas.targetCellType
            )
        }
      }
    }

  /**
   * Take a specific request for a map and combine it with the relevant [[OgcSource]] to produce an [[OgcLayer]]
   */
  def getLayer(p: GetMapParams): F[List[OgcLayer]] =
    parentLayerMeta.supportedProjections
      .find(_ == p.crs)
      .fold[F[List[OgcLayer]]](List.empty[OgcLayer].pure[F])(getLayer(p.toQuery, p.time, _, p.styles, p.params.some))

  /**
   * GetFeatureInfoExtended specific only
   */
  def getLayer(p: GetFeatureInfoExtendedParams): F[List[OgcLayer]] =
    parentLayerMeta.supportedProjections
      .find(_ == p.crs)
      .fold[F[List[OgcLayer]]](List.empty[OgcLayer].pure[F])(getLayer(p.toQuery, p.getTime, _, Nil, None))
}
