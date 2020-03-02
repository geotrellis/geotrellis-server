package geotrellis.server.ogc

import java.time.ZonedDateTime

/**
  * Represents the TimeInterval and TimePosition types used in TimeSequence requests
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
final case class OgcTimeInterval(start: ZonedDateTime,
                                 end: Option[ZonedDateTime],
                                 interval: Option[String]) {
  override def toString: String = {
    end match {
      case Some(e) => s"${start.toInstant.toString}/${e.toInstant.toString}${interval.map("/" + _).getOrElse("")}"
      case _ => start.toInstant.toString
    }
  }
}

object OgcTimeInterval {
  def apply(timePeriod: ZonedDateTime): OgcTimeInterval = OgcTimeInterval(timePeriod, None, None)

  def fromString(timeString: String): OgcTimeInterval = {
    val timeParts = timeString.split("/")
    timeParts match {
      case Array(start, end, interval) =>
        OgcTimeInterval(ZonedDateTime.parse(start), Some(ZonedDateTime.parse(end)), Some(interval))
      case Array(start, end) =>
        OgcTimeInterval(ZonedDateTime.parse(start), Some(ZonedDateTime.parse(end)), None)
      case Array(start) => OgcTimeInterval(ZonedDateTime.parse(start))
      case _ => throw new UnsupportedOperationException("Unsupported string format for OgcTimeInterval")
    }
  }
}
