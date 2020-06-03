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

  // dont use the word demo it confuses Eugene
  // and me
  //
  // Question for James and Chris
  // can it sort htwe return items?
  // mb it a franklin?>
  // limit + sort can fix
  // sort by time, to get the most recent?
  //
  // how this should look like?
  import geotrellis.store.query.QueryF._
  def algebra: Algebra[QueryF, SearchFilters] = Algebra {
    // unsupported node
    case Nothing() => SearchFilters()
    case All() => SearchFilters()
    case WithName(name) => SearchFilters(query = Map("layer:ids" -> Map("superset" -> List(name).asJson).asJson).asJsonObject)
    case WithNames(names) => SearchFilters(query = Map("layer:ids" -> Map("superset" -> names.asJson).asJson).asJsonObject)
    case At(t, _) => SearchFilters(datetime = TemporalExtent(t.toInstant, None).some)
    case Between(t1, t2, _) => SearchFilters(datetime = TemporalExtent(t1.toInstant, t2.toInstant).some)
    case Intersects(e) => SearchFilters(intersects = e.reproject(LatLng).extent.toPolygon.some)
    case Covers(e) =>
      val Extent(xmin, ymin, xmax, ymax) = e.reproject(LatLng).extent
      SearchFilters(bbox = TwoDimBbox(xmin, xmax, ymin, ymax).some)
    // unsupported node
    case Contains(_) => SearchFilters()
    case And(l, r) => l and r
    // unsupported node // weirdly supported node
    case Or(l, r) => l.copy(query = r.query)
  }

  def algebraWithNames(names: List[String]): Algebra[QueryF, SearchFilters] = Algebra {
    // unsupported node
    case Nothing() => SearchFilters()
    case All() => SearchFilters()
    case WithName(_) => SearchFilters(query = Map("layer:ids" -> Map("superset" -> names.take(1).asJson).asJson).asJsonObject)
    case WithNames(_) => SearchFilters(query = Map("layer:ids" -> Map("superset" -> names.asJson).asJson).asJsonObject)
    case At(t, _) => SearchFilters(datetime = TemporalExtent(t.toInstant, None).some)
    case Between(t1, t2, _) => SearchFilters(datetime = TemporalExtent(t1.toInstant, t2.toInstant).some)
    case Intersects(e) => SearchFilters(intersects = e.reproject(LatLng).extent.toPolygon.some)
    case Covers(e) =>
      val Extent(xmin, ymin, xmax, ymax) = e.reproject(LatLng).extent
      SearchFilters(bbox = TwoDimBbox(xmin, xmax, ymin, ymax).some)
    // unsupported node
    case Contains(_) => SearchFilters()
    case And(l, r) => l and r
    // unsupported node // weirdly supported node
    case Or(l, r) => l.copy(query = r.query)
  }

  def eval(query: Query): SearchFilters = scheme.cata(SearchFilters.algebra).apply(query)
  def eval(query: Query, names: List[String]): SearchFilters = scheme.cata(SearchFilters.algebraWithNames(names)).apply(query)
}
