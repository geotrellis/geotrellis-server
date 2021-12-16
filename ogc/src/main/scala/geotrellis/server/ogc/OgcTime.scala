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
import cats.{Monoid, Semigroup}
import cats.syntax.semigroup._
import io.circe.{Decoder, Encoder}
import jp.ne.opt.chronoscala.Imports._
import org.threeten.extra.PeriodDuration

import java.time.{Duration, ZonedDateTime}
import scala.util.Try

sealed trait OgcTime {
  def isEmpty: Boolean  = false
  def nonEmpty: Boolean = !isEmpty
}

object OgcTime {
  implicit val ogcTimeMonoid: Monoid[OgcTime] = new Monoid[OgcTime] {
    def empty: OgcTime = OgcTimeEmpty
    def combine(l: OgcTime, r: OgcTime): OgcTime =
      (l, r) match {
        case (l: OgcTimePositions, r: OgcTimePositions)  => l |+| r
        case (l: OgcTimeInterval, r: OgcTimeInterval)    => l |+| r
        case (l: OgcTimePositions, r: OgcTimeInterval)   => l.toOgcTimeInterval |+| r
        case (l: OgcTimeInterval, r: OgcTimePositions)   => l |+| r.toOgcTimeInterval
        case (l: OgcTimePositions, _: OgcTimeEmpty.type) => l
        case (l: OgcTimeInterval, _: OgcTimeEmpty.type)  => l
        case (_: OgcTimeEmpty.type, r: OgcTimePositions) => r
        case (_: OgcTimeEmpty.type, r: OgcTimeInterval)  => r
        case (l, _)                                      => l
      }
  }

  implicit def ogcTimeDecoder[T <: OgcTime]: Decoder[OgcTime] = Decoder.decodeString.map(fromString)
  implicit def ogcTimeEncoder[T <: OgcTime]: Encoder[OgcTime] = Encoder.encodeString.contramap(_.toString)

  def fromString(str: String): OgcTime =
    Try(OgcTimeInterval.fromString(str)).getOrElse(OgcTimePositions(str.split(",").toList))

  implicit class OgcTimeOps(val self: OgcTime) extends AnyVal {

    /** Reformat OgcTime if possible. */
    def format(format: OgcTimeFormat): OgcTime =
      format match {
        case OgcTimeFormat.Interval =>
          self match {
            case interval: OgcTimeInterval   => interval
            case positions: OgcTimePositions => positions.toOgcTimeInterval
            case _                           => self
          }
        case OgcTimeFormat.Positions =>
          self match {
            case interval: OgcTimeInterval   => interval.toTimePositions.getOrElse(interval)
            case positions: OgcTimePositions => positions
            case _                           => self
          }
        case OgcTimeFormat.Default => self
      }

    def strictTimeMatch(dt: ZonedDateTime): Boolean =
      self match {
        case OgcTimePositions(list)       => list.head == dt
        case OgcTimeInterval(start, _, _) => start == dt
        case OgcTimeEmpty                 => true
      }

    def sorted: OgcTime =
      self match {
        case OgcTimeEmpty           => OgcTimeEmpty
        case OgcTimePositions(list) => OgcTimePositions(list.sorted)
        case OgcTimeInterval(start, end, interval) =>
          val List(s, e) = List(start, end).sorted
          OgcTimeInterval(s, e, interval)

      }
  }
}

case object OgcTimeEmpty extends OgcTime {
  override val isEmpty: Boolean  = true
  override val nonEmpty: Boolean = !isEmpty

  implicit val ogcTimeEmptySemigroup: Semigroup[OgcTimeEmpty.type] = { (l, _) => l }
}

/** Represents the TimePosition used in TimeSequence requests */
final case class OgcTimePositions(list: NonEmptyList[ZonedDateTime]) extends OgcTime {
  def sorted: NonEmptyList[ZonedDateTime] = list.sorted

  /** Compute (if possible) the period of the [[ZonedDateTime]] lists. */
  def computeIntervalPeriod: Option[PeriodDuration] = {
    val periods =
      sorted.toList
        .sliding(2)
        .collect { case Seq(l, r, _*) => r.toEpochMilli - l.toEpochMilli }
        .toList
        .distinct
        .map(Duration.ofMillis)
        .map(PeriodDuration.of(_).normalizedStandardDays)

    if (periods.length < 2) periods.headOption
    else None
  }

  def toOgcTimeInterval: OgcTimeInterval = OgcTimeInterval(sorted.head, sorted.last, computeIntervalPeriod)

  def toList: List[String]      = list.toList.map(_.toInstant.toString)
  override def toString: String = toList.mkString(", ")
}

object OgcTimePositions {
  implicit val ogcTimePositionsSemigroup: Semigroup[OgcTimePositions] = { (l, r) =>
    OgcTimePositions((l.list ::: r.list).distinct.sorted)
  }

  def apply(timePeriod: ZonedDateTime): OgcTimePositions = OgcTimePositions(NonEmptyList(timePeriod, Nil))
  def apply(timeString: String): OgcTimePositions        = apply(ZonedDateTime.parse(timeString))
  def apply(times: List[ZonedDateTime]): OgcTime =
    times match {
      case head :: tail => OgcTimePositions(NonEmptyList(head, tail))
      case _            => OgcTimeEmpty
    }
  def apply(times: List[String])(implicit d: DummyImplicit): OgcTime = apply(times.map(ZonedDateTime.parse))

  def parse(times: List[String]): Try[OgcTime] = Try(apply(times.map(ZonedDateTime.parse)))
}

/**
 * Represents the TimeInterval used in TimeSequence requests
 *
 * If end is provided, a TimeInterval is assumed. Otherwise, a TimePosition.
 *
 * @param start
 *   The start time for this TimePosition or TimeInterval
 * @param end
 *   The end time for this TimeInterval. If None, a TimePosition is assumed from the start param.
 * @param interval
 *   ISO 8601:2000 provides a syntax for expressing time periods: the designator P, followed by a number of years Y, months M, days D, a time
 *   designator T, number of hours H, minutes M, and seconds S. Unneeded elements may be omitted. Here are a few examples: EXAMPLE 1 - P1Y, 1 year
 *   EXAMPLE 2 - P1M10D, 1 month plus 10 days EXAMPLE 3 - PT2H, 2 hours
 *
 * @note
 *   This param is not validated. It is up to the user to ensure that it is encoded directly
 */
final case class OgcTimeInterval(start: ZonedDateTime, end: ZonedDateTime, interval: Option[PeriodDuration]) extends OgcTime {
  def toTimePositions: Option[OgcTimePositions] =
    interval.flatMap { pd =>
      val positions =
        (start.toEpochMilli to end.toEpochMilli by pd.toMillis)
          .map(Instant.ofEpochMilli)
          .map(ZonedDateTime.ofInstant(_, start.getZone))
          .toList

      NonEmptyList.fromList(positions).map(OgcTimePositions.apply)
    }

  override def toString: String =
    if (start != end) s"${start.toInstant.toString}/${end.toInstant.toString}${interval.map(i => s"/$i").getOrElse("")}"
    else start.toInstant.toString
}

object OgcTimeInterval {

  /** Safe [[PeriodDuration]] parser. */
  private def periodDurationParse(string: String): Option[PeriodDuration] = Try(PeriodDuration.parse(string)).toOption

  /**
   * Merge two OgcTimeInterval instances This semigroup instance destroys the interval. If you need to retain interval when combining instances,
   * perform this operation yourself.
   */
  implicit val ogcTimeIntervalSemigroup: Semigroup[OgcTimeInterval] = { (l, r) =>
    val times = List(l.start, l.end, r.start, r.end).sorted
    OgcTimeInterval(times.head, times.last, if (l.interval == r.interval) l.interval else None)
  }

  def apply(start: ZonedDateTime): OgcTimeInterval = OgcTimeInterval(start, start, None)

  def apply(start: ZonedDateTime, end: ZonedDateTime): OgcTimeInterval = OgcTimeInterval(start, end, None)

  def apply(start: ZonedDateTime, end: ZonedDateTime, interval: String): OgcTimeInterval = OgcTimeInterval(start, end, periodDurationParse(interval))

  def apply(timeString: String): OgcTimeInterval = fromString(timeString)

  def fromString(timeString: String): OgcTimeInterval = {
    val timeParts = timeString.split("/")
    timeParts match {
      case Array(start, end, interval) => OgcTimeInterval(ZonedDateTime.parse(start), ZonedDateTime.parse(end), periodDurationParse(interval))
      case Array(start, end)           => OgcTimeInterval(ZonedDateTime.parse(start), ZonedDateTime.parse(end), None)
      case Array(start)                => OgcTimeInterval(ZonedDateTime.parse(start))
      case _                           => throw new UnsupportedOperationException("Unsupported string format for OgcTimeInterval")
    }
  }

  def parse(timeString: String): Try[OgcTimeInterval] = Try(fromString(timeString))
}
