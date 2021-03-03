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

package geotrellis.server.ogc.stac

import geotrellis.stac._
import geotrellis.store.query.{Query, QueryF}
import geotrellis.proj4.LatLng
import com.azavea.stac4s.{Bbox, TwoDimBbox}
import com.azavea.stac4s.types.TemporalExtent
import com.azavea.stac4s.api.client.{SearchFilters, Superset}
import io.circe.syntax._
import higherkindness.droste.{scheme, Algebra}
import geotrellis.vector._
import cats.{Order, Semigroup}
import cats.data.NonEmptyVector
import cats.syntax.option._
import cats.syntax.either._
import cats.syntax.semigroup._
import cats.instances.option._
import cats.instances.list._
import eu.timepit.refined.cats._
import eu.timepit.refined.types.numeric.NonNegInt

object SearchFiltersOps {
  // overcome diverging implicit expansion
  implicit private val nonNegIntOrder: Order[NonNegInt]       = refTypeOrder
  implicit private val nonNegIntOrdering: Ordering[NonNegInt] = nonNegIntOrder.toOrdering

  object IntersectionSemigroup {
    implicit val bboxIntersectionSemigroup: Semigroup[Bbox] = { (left, right) =>
      val Extent(xmin, ymin, xmax, ymax) =
        left.toExtent
          .flatMap { l =>
            right.toExtent
              .flatMap(l.intersection(_).toRight(s"$left and $right have no intersections"))
          }
          .valueOr(str => throw new IllegalArgumentException(str))

      TwoDimBbox(xmin, ymin, xmax, ymax)
    }

    implicit val temporalExtentSemigroup: Semigroup[TemporalExtent] = { (left, right) =>
      val (lmin, lmax) = left.value.min  -> left.value.max
      val (rmin, rmax) = right.value.min -> right.value.max
      TemporalExtent.unsafeFrom(
        List(
          List(lmin, rmin).max,
          List(lmax, rmax).min
        )
      )
    }

    implicit val geometryIntersectionSemigroup: Semigroup[Geometry] = { _ intersection _ }

    implicit val searchFiltersSemigroup: Semigroup[SearchFilters] = { (left, right) =>
      SearchFilters(
        bbox = left.bbox |+| right.bbox,
        datetime = left.datetime |+| right.datetime,
        intersects = left.intersects |+| right.intersects,
        collections = (left.collections |+| right.collections).distinct,
        items = (left.collections |+| right.collections).distinct,
        limit = List(left.limit, right.limit).min,
        next = right.next,
        query = left.query.deepMerge(right.query)
      )
    }
  }

  object UnionSemigroup {
    implicit val bboxUnionSemigroup: Semigroup[Bbox] = { (left, right) =>
      val Extent(xmin, ymin, xmax, ymax) =
        left.toExtent
          .flatMap { l =>
            right.toExtent
              .flatMap(l.combine(_).some.toRight(s"$left and $right have no intersections"))
          }
          .valueOr(str => throw new IllegalArgumentException(str))

      TwoDimBbox(xmin, ymin, xmax, ymax)
    }

    implicit val temporalExtentSemigroup: Semigroup[TemporalExtent] = { (left, right) =>
      val (lmin, lmax) = left.value.min  -> left.value.max
      val (rmin, rmax) = right.value.min -> right.value.max
      TemporalExtent.unsafeFrom(
        List(
          List(lmin, rmin).min,
          List(lmax, rmax).max
        )
      )
    }

    implicit val geometryUnionSemigroup: Semigroup[Geometry] = { _ union _ }

    implicit val searchFiltersSemigroup: Semigroup[SearchFilters] = { (left, right) =>
      SearchFilters(
        bbox = left.bbox |+| right.bbox,
        datetime = left.datetime |+| right.datetime,
        intersects = left.intersects |+| right.intersects,
        collections = (left.collections |+| right.collections).distinct,
        items = (left.collections |+| right.collections).distinct,
        limit = List(left.limit, right.limit).min,
        next = right.next,
        query = left.query.deepMerge(right.query)
      )
    }
  }

  import geotrellis.store.query.QueryF._
  def algebra: Algebra[QueryF, Option[SearchFilters]] =
    Algebra {
      case Nothing()          => None
      case All()              => SearchFilters().some
      case WithName(name)     => SearchFilters(query = Map("layer:ids" -> List(Superset(NonEmptyVector.one(name.asJson))))).some
      case WithNames(names)   =>
        SearchFilters(query = Map("layer:ids" -> List(Superset(NonEmptyVector.fromVectorUnsafe(names.map(_.asJson).toVector))))).some
      case At(t, _)           => SearchFilters(datetime = TemporalExtent(t.toInstant, t.toInstant).some).some
      case Between(t1, t2, _) => SearchFilters(datetime = TemporalExtent(t1.toInstant, t2.toInstant).some).some
      case Intersects(e)      => SearchFilters(intersects = e.reproject(LatLng).extent.toPolygon.some).some
      case Covers(e)          => SearchFilters(bbox = e.reproject(LatLng).extent.toTwoDimBbox.some).some
      case And(l, r)          => import IntersectionSemigroup._; l |+| r
      case Or(l, r)           => import UnionSemigroup._; l |+| r
      // unsupported nodes
      case _                  => SearchFilters().some
    }

  def eval(query: Query): Option[SearchFilters] = scheme.cata(SearchFiltersOps.algebra).apply(query)
}
