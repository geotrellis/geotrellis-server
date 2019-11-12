package geotrellis.server

import java.time.Instant

import cats.implicits._
import com.github.tbouron.SpdxLicense
import eu.timepit.refined._
import eu.timepit.refined.api.{RefType, Refined, RefinedTypeOps, Validate}
import eu.timepit.refined.boolean._
import eu.timepit.refined.collection.{Exists, MinSize, _}
import geotrellis.vector.{io => _, _}
import io.circe._
import io.circe.parser.{decode, parse}
import io.circe.shapes.CoproductInstances
import io.circe.syntax._
import shapeless._

package object stac {

  type SpdxId = String Refined ValidSpdxId
  object SpdxId extends RefinedTypeOps[SpdxId, String]

  case class ValidSpdxId()

  object ValidSpdxId {
    implicit def validSpdxId: Validate.Plain[String, ValidSpdxId] =
      Validate.fromPredicate(
        SpdxLicense.isValidId,
        t => s"Invalid SPDX Id: $t",
        ValidSpdxId()
      )
  }

  case class HasInstant()

  object HasInstant {
    implicit def hasInstant: Validate.Plain[Option[Instant], HasInstant] =
      Validate.fromPredicate(
        {
          case None => false
          case _    => true
        },
        t => s"Value Is None: $t",
        HasInstant()
      )
  }

  type TemporalExtent =
    List[Option[Instant]] Refined And[
      And[MinSize[W.`2`.T], MaxSize[W.`2`.T]],
      Exists[HasInstant]
    ]

  object TemporalExtent
      extends RefinedTypeOps[TemporalExtent, List[Option[Instant]]] {
    def apply(start: Instant, end: Option[Instant]): TemporalExtent =
      TemporalExtent.unsafeFrom(List(Some(start), end))
    def apply(start: Option[Instant], end: Instant): TemporalExtent =
      TemporalExtent.unsafeFrom(List(start, Some(end)))
    def apply(start: Instant, end: Instant): TemporalExtent =
      TemporalExtent.unsafeFrom(List(Some(start), Some(end)))

  }

  object Implicits {

    // Stolen straight from circe docs
    implicit val decodeInstant: Decoder[Instant] = Decoder.decodeString.emap {
      str =>
        Either
          .catchNonFatal(Instant.parse(str))
          .leftMap(t => "Instant")
    }

    implicit val encodeInstant: Encoder[Instant] =
      Encoder.encodeString.contramap[Instant](_.toString)

    implicit val encodeTemporalExtent =
      new Encoder[TemporalExtent] {
        final def apply(t: TemporalExtent): Json = {
          t.value.map(x => x.asJson).asJson
        }
      }
    implicit val decodeTemporalExtent =
      Decoder.decodeList[Option[Instant]].emap {
        case l =>
          RefType.applyRef[TemporalExtent](l)
      }

    implicit val geometryDecoder: Decoder[Geometry] = Decoder[Json] map { js =>
      js.spaces4.parseGeoJson[Geometry]
    }

    implicit val geometryEncoder: Encoder[Geometry] = new Encoder[Geometry] {
      def apply(geom: Geometry) = {
        parse(geom.toGeoJson) match {
          case Right(js) => js
          case Left(e)   => throw e
        }
      }
    }

    implicit val decTimeRange
        : Decoder[(Option[Instant], Option[Instant])] = Decoder[String] map {
      str =>
        val components = str.replace("[", "").replace("]", "").split(",") map {
          _.trim
        }
        components match {
          case parts if parts.length == 2 =>
            val start = parts.head
            val end = parts.drop(1).head
            (decode[Instant](start).toOption, decode[Instant](end).toOption)
          case parts if parts.length > 2 =>
            val message = "Too many elements for temporal extent: $parts"
            throw new ParsingFailure(message, new Exception(message))
          case parts if parts.length < 2 =>
            val message = "Too few elements for temporal extent: $parts"
            throw new ParsingFailure(message, new Exception(message))
        }
    }
  }
}
