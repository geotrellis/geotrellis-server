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

import geotrellis.vector.ProjectedExtent

import io.circe._
import io.circe.syntax._
import io.circe.generic.JsonCodec
import cats.Functor
import cats.syntax.either._ // scala 2.11 compact
import higherkindness.droste.scheme
import higherkindness.droste.{Algebra, Coalgebra}
import higherkindness.droste.syntax.fix._

import java.time.ZonedDateTime

@JsonCodec sealed trait QueryF[A]

object QueryF {
  // contains java8.time codecs for scala 2.11
  import CirceCompat._

  /** Tree leaves */
  @JsonCodec case class Or[A](left: A, right: A) extends QueryF[A]
  @JsonCodec case class And[A](left: A, right: A) extends QueryF[A]
  @JsonCodec case class Intersects[A](projectedExtent: ProjectedExtent) extends QueryF[A]
  @JsonCodec case class Contains[A](projectedExtent: ProjectedExtent) extends QueryF[A]
  @JsonCodec case class Covers[A](projectedExtent: ProjectedExtent) extends QueryF[A]
  @JsonCodec case class At[A](time: ZonedDateTime, fieldName: String = "time") extends QueryF[A]
  @JsonCodec case class Between[A](from: ZonedDateTime, to: ZonedDateTime, fieldName: String = "time") extends QueryF[A]
  @JsonCodec case class WithName[A](name: String) extends QueryF[A]
  @JsonCodec case class WithNames[A](names: Set[String]) extends QueryF[A]
  @JsonCodec case class Nothing[A]() extends QueryF[A]
  @JsonCodec case class All[A]() extends QueryF[A]

  /** Build Tree syntax */
  def or(left: Query, right: Query): Query                = Or(left, right).fix
  def and(left: Query, right: Query): Query               = And(left, right).fix
  def nothing: Query                                      = Nothing().fix
  def all: Query                                          = All().fix
  def withName(name: String): Query                       = WithName(name).fix
  def withNames(names: Set[String]): Query                = WithNames(names).fix
  def intersects(projectedExtent: ProjectedExtent): Query = Intersects(projectedExtent).fix
  def contains(projectedExtent: ProjectedExtent): Query   = Contains(projectedExtent).fix
  def covers(projectedExtent: ProjectedExtent): Query     = Covers(projectedExtent).fix
  def at(time: ZonedDateTime, fieldName: String = "time"): Query                         = At(time, fieldName).fix
  def between(from: ZonedDateTime, to: ZonedDateTime, fieldName: String = "time"): Query = Between(from, to, fieldName).fix

  /** Pattern functor for QueryF */
  implicit val queryFFunctor: Functor[QueryF] = new Functor[QueryF] {
    def map[A, B](fa: QueryF[A])(f: A => B): QueryF[B] = fa match {
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
    def onNull: QueryF[Json]                       = QueryF.Nothing()
    def onBoolean(value: Boolean): QueryF[Json]    = QueryF.Nothing()
    def onNumber(value: JsonNumber): QueryF[Json]  = QueryF.Nothing()
    def onString(value: String): QueryF[Json]      = QueryF.Nothing()
    def onArray(value: Vector[Json]): QueryF[Json] = QueryF.Nothing()
    def onObject(value: JsonObject): QueryF[Json]  =
      value
        .asJson
        .as[QueryF[Json]]
        .getOrElse(QueryF.Nothing[Json]())
  }

  val coalgebraJson: Coalgebra[QueryF, Json] = Coalgebra(_.foldWith(unfolder))

  def asJson(query: Query): Json  = scheme.cata(algebraJson).apply(query)
  def fromJson(json: Json): Query = scheme.ana(coalgebraJson).apply(json)
}
