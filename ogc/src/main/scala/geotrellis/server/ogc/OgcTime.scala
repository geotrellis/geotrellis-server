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

package geotrellis.server.ogc

import cats.data.NonEmptyList
import cats.{Monoid, Order, Semigroup}
import cats.syntax.option._
import cats.syntax.semigroup._
import jp.ne.opt.chronoscala.Imports._

import java.time.ZonedDateTime
import scala.util.Try

sealed trait OgcTime {
  def isEmpty: Boolean  = false
  def nonEmpty: Boolean = !isEmpty
}

object OgcTime {
  implicit val ogcTimeMonoid: Monoid[OgcTime] = new Monoid[OgcTime] {
    def empty: OgcTime                           = OgcTimeEmpty
    def combine(l: OgcTime, r: OgcTime): OgcTime =
      (l, r) match {
        case (l: OgcTimePositions, r: OgcTimePositions)  => l |+| r
        case (l: OgcTimeInterval, r: OgcTimeInterval)    => l |+| r
        case (l: OgcTimePositions, _: OgcTimeEmpty.type) => l
        case (l: OgcTimeInterval, _: OgcTimeEmpty.type)  => l
        case (_: OgcTimeEmpty.type, r: OgcTimePositions) => r
        case (_: OgcTimeEmpty.type, r: OgcTimeInterval)  => r
        case (l, _)                                      => l
      }
  }

  def strictTimeMatch(time: OgcTime, dt: ZonedDateTime): Boolean =
    time match {
      case OgcTimePositions(list)       => list.head == dt
      case OgcTimeInterval(start, _, _) => start == dt
      case OgcTimeEmpty                 => true
    }

  def fromString(str: String): OgcTime =
    Try(OgcTimeInterval.fromString(str))
      .toOption
      .getOrElse(OgcTimePositions(str.split(",").toList))
}

case object OgcTimeEmpty extends OgcTime {
  override val isEmpty: Boolean  = true
  override val nonEmpty: Boolean = !isEmpty

  implicit val ogcTimeEmptySemigroup: Semigroup[OgcTimeEmpty.type] = { (l, _) => l }
}

/** Represents the TimePosition used in TimeSequence requests */
final case class OgcTimePositions(list: NonEmptyList[ZonedDateTime]) extends OgcTime {
  import OgcTimePositions._

  def toOgcTimeInterval: OgcTimeInterval = {
    val times = list.sorted
    OgcTimeInterval(times.head, times.last, None)
  }
  override def toString: String = list.toList.map(_.toInstant.toString).mkString(", ")
}

object OgcTimePositions {
  implicit val timeOrder: Order[ZonedDateTime] = Order.fromOrdering

  implicit val ogcTimePositionsSemigroup: Semigroup[OgcTimePositions] = { (l, r) =>
    OgcTimePositions((l.list ::: r.list).distinct.sorted)
  }

  def apply(timePeriod: ZonedDateTime): OgcTimePositions             = OgcTimePositions(NonEmptyList(timePeriod, Nil))
  def apply(timeString: String): OgcTimePositions                    = apply(ZonedDateTime.parse(timeString))
  def apply(times: List[ZonedDateTime]): OgcTime                     =
    times match {
      case head :: tail => OgcTimePositions(NonEmptyList(head, tail))
      case _            => OgcTimeEmpty
    }
  def apply(times: List[String])(implicit d: DummyImplicit): OgcTime = apply(times.map(ZonedDateTime.parse))
}

/**
  * Represents the TimeInterval used in TimeSequence requests
  *
  * If end is provided, a TimeInterval is assumed. Otherwise, a TimePosition.
  *
  * @param start The start time for this TimePosition or TimeInterval
  * @param end The end time for this TimeInterval. If None, a TimePosition is assumed from the
  *              start param.
  * @param interval ISO 8601:2000 provides a syntax for expressing time periods: the designator P,
  *                 followed by a number of years Y, months M, days D, a time designator T, number
  *                 of hours H, minutes M, and seconds S. Unneeded elements may be omitted. Here are
  *                 a few examples:
  *                 EXAMPLE 1 -  P1Y, 1 year
  *                 EXAMPLE 2 - P1M10D, 1 month plus 10 days
  *                 EXAMPLE 3 - PT2H, 2 hours
  *
  *                 @note This param is not validated. It is up to the user to ensure that it is
  *                       encoded directly
  */
final case class OgcTimeInterval(start: ZonedDateTime, end: ZonedDateTime, interval: Option[String]) extends OgcTime {
  override def toString: String =
    if (start != end) s"${start.toInstant.toString}/${end.toInstant.toString}${interval.map("/" + _).getOrElse("")}"
    else start.toInstant.toString
}

object OgcTimeInterval {

  /**
    * Merge two OgcTimeInterval instances
    * This semigroup instance destroys the interval. If you need to retain interval when combining
    *  instances, perform this operation yourself.
    */
  implicit val ogcTimeIntervalSemigroup: Semigroup[OgcTimeInterval] = { (l, r) =>
    val times = List(l.start, l.end, r.start, r.end).sorted
    OgcTimeInterval(times.head, times.last, None)
  }

  def apply(timePeriod: ZonedDateTime): OgcTimeInterval = OgcTimeInterval(timePeriod, timePeriod, None)

  def apply(timeString: String): OgcTimeInterval = fromString(timeString)

  def fromString(timeString: String): OgcTimeInterval = {
    val timeParts = timeString.split("/")
    timeParts match {
      case Array(start, end, interval) => OgcTimeInterval(ZonedDateTime.parse(start), ZonedDateTime.parse(end), interval.some)
      case Array(start, end)           => OgcTimeInterval(ZonedDateTime.parse(start), ZonedDateTime.parse(end), None)
      case Array(start)                => OgcTimeInterval(ZonedDateTime.parse(start))
      case _                           => throw new UnsupportedOperationException("Unsupported string format for OgcTimeInterval")
    }
  }
}
