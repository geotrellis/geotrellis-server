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

package geotrellis.stac.api

import com.azavea.stac4s.{Bbox, TemporalExtent, TwoDimBbox}
import geotrellis.store.query.{Query, QueryF}
import higherkindness.droste.{Algebra, scheme}
import cats.syntax.option._
import geotrellis.proj4.LatLng
import io.circe._
import io.circe.generic.semiauto._
import io.circe.syntax._
import geotrellis.vector._

case class SearchFilters(
  bbox: Option[Bbox] = None,
  datetime: Option[TemporalExtent] = None,
  intersects: Option[Geometry] = None,
  collections: List[String] = Nil,
  items: List[String] = Nil,
  limit: Option[Int] = None,
  next: Option[String] = None,
  query: JsonObject = JsonObject.empty
) {
  def and(other: SearchFilters): SearchFilters = {
    SearchFilters(
      bbox = bbox orElse other.bbox,
      datetime = (datetime, other.datetime) match {
        case (Some(left), Some(right)) =>
          val (lmin, lmax) = left.value.min -> left.value.max
          val (rmin, rmax) = right.value.min -> right.value.max
          TemporalExtent.from(List(
            List(lmin, rmin).max,
            List(lmax, rmax).min
          )).toOption
        case (l @ Some(_), _) => l
        case (_, r @ Some(_)) => r
        case _ => None
      },
      intersects = (intersects, other.intersects) match {
        case (Some(l), Some(r)) => l.intersection(r).some
        case (l @ Some(_), _) => l
        case (_,  r @ Some(_)) => r
        case _ => None
      },
      collections = (collections ++ other.collections).distinct,
      items = (collections ++ other.collections).distinct,
      limit = List(limit, other.limit).min,
      next = List(next, other.next).max,
      query = query.deepMerge(other.query)
    )
  }
}

object SearchFilters {

  implicit val searchFilterDecoder: Decoder[SearchFilters] = { c =>
    for {
      bbox <- c.downField("bbox").as[Option[Bbox]]
      datetime <- c.downField("datetime").as[Option[TemporalExtent]]
      intersects <- c.downField("intersects").as[Option[Geometry]]
      collectionsOption <- c.downField("collections").as[Option[List[String]]]
      itemsOption <- c.downField("items").as[Option[List[String]]]
      limit <- c.downField("limit").as[Option[Int]]
      next <- c.downField("next").as[Option[String]]
      query <- c.get[JsonObject]("query")
    } yield {
      SearchFilters(
        bbox,
        datetime,
        intersects,
        collectionsOption.getOrElse(Nil),
        itemsOption.getOrElse(Nil),
        limit,
        next,
        query
      )
    }
  }


  implicit val searchFilterEncoder: Encoder[SearchFilters] = deriveEncoder

  import geotrellis.store.query.QueryF._
  def algebra: Algebra[QueryF, SearchFilters] = Algebra {
    // unsupported node
    case Nothing()          => SearchFilters()
    case All()              => SearchFilters()
    case WithName(name)     => SearchFilters(query = Map("layer:ids" -> Map("superset" -> List(name).asJson).asJson).asJsonObject)
    case WithNames(names)   => SearchFilters(query = Map("layer:ids" -> Map("superset" -> names.asJson).asJson).asJsonObject)
    case At(t, _)           => SearchFilters(datetime = TemporalExtent(t.toInstant, None).some)
    case Between(t1, t2, _) => SearchFilters(datetime = TemporalExtent(t1.toInstant, t2.toInstant).some)
    case Intersects(e)      => SearchFilters(intersects = e.reproject(LatLng).extent.toPolygon.some)
    case Covers(e) =>
      val Extent(xmin, ymin, xmax, ymax) = e.reproject(LatLng).extent
      SearchFilters(bbox = TwoDimBbox(xmin, xmax, ymin, ymax).some)
    // unsupported node
    case Contains(_) => SearchFilters()
    case And(l, r) => l and r
    // unsupported node
    case Or(_, _)  => SearchFilters()
  }

  def eval(query: Query): SearchFilters = scheme.cata(SearchFilters.algebra).apply(query)
}