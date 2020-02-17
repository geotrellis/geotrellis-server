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

import cats.Functor
import higherkindness.droste.syntax.fix._

import java.time.ZonedDateTime

trait QueryF[A]

object QueryF {
  /** Tree leaves */
  case class Or[A](l: A, r: A) extends QueryF[A]
  case class And[A](l: A, r: A) extends QueryF[A]
  case class Intersects[A](pe: ProjectedExtent) extends QueryF[A]
  case class Contains[A](pe: ProjectedExtent) extends QueryF[A]
  case class Covers[A](pe: ProjectedExtent) extends QueryF[A]
  case class At[A](t: ZonedDateTime, fieldName: Symbol = 'time) extends QueryF[A]
  case class Between[A](from: ZonedDateTime, to: ZonedDateTime, fieldName: Symbol = 'time) extends QueryF[A]
  case class WithName[A](name: String) extends QueryF[A]
  case class WithNames[A](names: Set[String]) extends QueryF[A]
  case class Nothing[A]() extends QueryF[A]
  case class All[A]() extends QueryF[A]

  /** Build Tree syntax */
  def or(l: Query, r: Query): Query          = Or(l, r).fix
  def and(l: Query, r: Query): Query         = And(l, r).fix
  def nothing: Query                         = Nothing().fix
  def all: Query                             = All().fix
  def withName(name: String): Query          = WithName(name).fix
  def withNames(names: Set[String]): Query   = WithNames(names).fix
  def intersects(pe: ProjectedExtent): Query = Intersects(pe).fix
  def contains(pe: ProjectedExtent): Query   = Contains(pe).fix
  def covers(pe: ProjectedExtent): Query     = Covers(pe).fix
  def at(t: ZonedDateTime, fieldName: Symbol = 'time): Query                          = At(t, fieldName).fix
  def between(t1: ZonedDateTime, t2: ZonedDateTime, fieldName: Symbol = 'time): Query = Between(t1, t2, fieldName).fix

  /** Pattern functor for QueryF */
  implicit val queryFFunctor: Functor[QueryF] = new Functor[QueryF] {
    def map[A, B](fa: QueryF[A])(f: A => B): QueryF[B] = fa match {
      case And(l, r)           => And(f(l), f(r))
      case Or(l, r)            => Or(f(l), f(r))
      case At(v, fn)           => At[B](v, fn)
      case Between(t1, t2, fn) => Between[B](t1, t2, fn)
      case Intersects(v)       => Intersects[B](v)
      case Contains(v)         => Contains[B](v)
      case Covers(v)           => Covers[B](v)
      case WithName(v)         => WithName[B](v)
      case WithNames(v)        => WithNames[B](v)
      case Nothing()           => Nothing[B]()
      case All()               => All[B]()
    }
  }
}
