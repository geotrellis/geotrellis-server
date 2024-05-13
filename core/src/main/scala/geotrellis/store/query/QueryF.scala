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

package geotrellis.store.query

import geotrellis.vector.{Geometry, ProjectedExtent}
import io.circe._
import io.circe.syntax._
import io.circe.generic.JsonCodec
import cats.Functor
import cats.data.NonEmptySet
import geotrellis.store.query.vector.ProjectedGeometry
import higherkindness.droste.scheme
import higherkindness.droste.{Algebra, Coalgebra}
import higherkindness.droste.syntax.fix._
import higherkindness.droste.syntax.unfix._

import java.time.ZonedDateTime

@JsonCodec sealed trait QueryF[A]

object QueryF {

  /**
   * Tree leaves
   */
  @JsonCodec case class Or[A](left: A, right: A) extends QueryF[A]
  @JsonCodec case class And[A](left: A, right: A) extends QueryF[A]
  @JsonCodec case class Intersects[A](projectedGeometry: ProjectedGeometry) extends QueryF[A]
  @JsonCodec case class Contains[A](projectedGeometry: ProjectedGeometry) extends QueryF[A]
  @JsonCodec case class Covers[A](projectedGeometry: ProjectedGeometry) extends QueryF[A]
  @JsonCodec case class At[A](time: ZonedDateTime, fieldName: String = "time") extends QueryF[A]
  @JsonCodec case class Between[A](from: ZonedDateTime, to: ZonedDateTime, fieldName: String = "time") extends QueryF[A]
  @JsonCodec case class WithName[A](name: String) extends QueryF[A]
  @JsonCodec case class WithNames[A](names: Set[String]) extends QueryF[A]
  @JsonCodec case class Nothing[A]() extends QueryF[A]
  @JsonCodec case class All[A]() extends QueryF[A]

  /**
   * Build Tree syntax
   */
  def or(left: Query, right: Query): Query = Or(left, right).fix
  def and(left: Query, right: Query): Query = And(left, right).fix
  def nothing: Query = Nothing().fix
  def all: Query = All().fix
  def withName(name: String): Query = WithName(name).fix
  def withNames(names: Set[String]): Query = WithNames(names).fix
  def intersects(projectedGeometry: ProjectedGeometry): Query = Intersects(projectedGeometry).fix
  def contains(projectedGeometry: ProjectedGeometry): Query = Contains(projectedGeometry).fix
  def covers(projectedGeometry: ProjectedGeometry): Query = Covers(projectedGeometry).fix
  def at(time: ZonedDateTime, fieldName: String = "time"): Query = At(time, fieldName).fix

  def between(from: ZonedDateTime, to: ZonedDateTime, fieldName: String = "time"): Query =
    Between(from, to, fieldName).fix

  /**
   * Pattern functor for QueryF
   */
  implicit val queryFFunctor: Functor[QueryF] = new Functor[QueryF] {
    def map[A, B](fa: QueryF[A])(f: A => B): QueryF[B] =
      fa match {
        case And(l, r)         => And(f(l), f(r))
        case Or(l, r)          => Or(f(l), f(r))
        case At(v, fn)         => At[B](v, fn)
        case Between(f, t, fn) => Between[B](f, t, fn)
        case Intersects(v)     => Intersects[B](v)
        case Contains(v)       => Contains[B](v)
        case Covers(v)         => Covers[B](v)
        case WithName(v)       => WithName[B](v)
        case WithNames(v)      => WithNames[B](v)
        case Nothing()         => Nothing[B]()
        case All()             => All[B]()
      }
  }

  val algebraJson: Algebra[QueryF, Json] = Algebra(_.asJson)

  val unfolder: Json.Folder[QueryF[Json]] = new Json.Folder[QueryF[Json]] {
    def onNull: QueryF[Json] = QueryF.Nothing()
    def onBoolean(value: Boolean): QueryF[Json] = QueryF.Nothing()
    def onNumber(value: JsonNumber): QueryF[Json] = QueryF.Nothing()
    def onString(value: String): QueryF[Json] = QueryF.Nothing()
    def onArray(value: Vector[Json]): QueryF[Json] = QueryF.Nothing()
    def onObject(value: JsonObject): QueryF[Json] =
      value.asJson
        .as[QueryF[Json]]
        .getOrElse(QueryF.Nothing[Json]())
  }

  val coalgebraJson: Coalgebra[QueryF, Json] = Coalgebra(_.foldWith(unfolder))

  /**
   * Coalgebras that replace certain nodes
   */
  def coalgebraOverrideName(name: String): Coalgebra[QueryF, Query] =
    Coalgebra {
      case WithName(_)  => WithName(name)
      case WithNames(_) => WithNames(Set(name))
      case e            => e.unfix
    }

  def coalgebraOverrideNames(names: NonEmptySet[String]): Coalgebra[QueryF, Query] =
    Coalgebra {
      case WithNames(_) => WithNames(names.toSortedSet)
      case e            => e.unfix
    }

  val algebraIsTemporal: Algebra[QueryF, Boolean] =
    Algebra {
      case At(_, _)         => true
      case Between(_, _, _) => true
      case And(e1, e2)      => e1 || e2
      case Or(e1, e2)       => e1 || e2
      case _                => false
    }

  val algebraIsUniversal: Algebra[QueryF, Boolean] =
    Algebra {
      case At(_, _)         => false
      case Between(_, _, _) => false
      case Intersects(_)    => false
      case Contains(_)      => false
      case Covers(_)        => false
      case Nothing()        => false
      case And(e1, e2)      => e1 && e2
      case Or(e1, e2)       => e1 || e2
      case _                => true
    }

  def asJson(query: Query): Json = scheme.cata(algebraJson).apply(query)
  def fromJson(json: Json): Query = scheme.ana(coalgebraJson).apply(json)
  def isTemporal(query: Query): Boolean = scheme.cata(algebraIsTemporal).apply(query)
  def isUniversal(query: Query): Boolean = scheme.cata(algebraIsUniversal).apply(query)
  def overrideName(query: Query, name: String): Query = scheme.ana(coalgebraOverrideName(name)).apply(query)
}
