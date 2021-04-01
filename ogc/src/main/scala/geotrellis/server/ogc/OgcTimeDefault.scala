package geotrellis.server.ogc

import cats.data.NonEmptyList
import io.circe.Decoder
import cats.syntax.either._

import java.time.ZonedDateTime
import scala.util.Try

sealed trait OgcTimeDefault {
  lazy val name: String = getClass.getName.split("\\.").last.toLowerCase
}

object OgcTimeDefault {
  case object Oldest                   extends OgcTimeDefault
  case object Newest                   extends OgcTimeDefault
  case class Time(time: ZonedDateTime) extends OgcTimeDefault

  def fromString(str: String): OgcTimeDefault = str match {
    case Oldest.name => Oldest
    case Newest.name => Newest
    case _           => Time(ZonedDateTime.parse(str))
  }

  implicit val ogcTimeDefaultDecoder: Decoder[OgcTimeDefault] = Decoder[String].emap { s =>
    Try(fromString(s)).toEither.leftMap(_.getMessage)
  }

  implicit class OgcTimeDefaultOps(val self: OgcTimeDefault) extends AnyVal {
    def selectTime(list: NonEmptyList[ZonedDateTime]): ZonedDateTime =
      self match {
        case OgcTimeDefault.Oldest  => list.head
        case OgcTimeDefault.Newest  => list.last
        case OgcTimeDefault.Time(t) => t
      }
  }
}
