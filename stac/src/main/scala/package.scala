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

  case class LicenseLink()

  object LicenseLink {
    implicit def validateLicenseLink: Validate.Plain[StacLink, LicenseLink] =
      Validate.fromPredicate(
        l =>
          l.rel match {
            case License => true
            case _       => false
          },
        t => s"Not a License Link: ${t.rel}",
        LicenseLink()
      )
  }

  type StacLinksWithLicense = List[StacLink] Refined Exists[LicenseLink]
  object StacLinksWithLicense
      extends RefinedTypeOps[StacLinksWithLicense, List[StacLink]] {
    def fromStacLinkWithLicense(
        links: List[StacLink],
        href: String,
        stacMediaType: Option[StacMediaType],
        title: Option[String]
    ): StacLinksWithLicense = {
      val licenseLink = StacLink(
        href,
        License,
        stacMediaType,
        title,
        List.empty[String]
      )
      StacLinksWithLicense.unsafeFrom(licenseLink :: links)
    }
  }

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

  implicit val encodeTemporalExtent: Encoder[TemporalExtent] =
    new Encoder[TemporalExtent] {
      final def apply(t: TemporalExtent): Json = {
        t.value.map(x => x.asJson).asJson
      }
    }
  implicit val decodeTemporalExtent: Decoder[TemporalExtent] =
    Decoder.decodeList[Option[Instant]].emap {
      case l =>
        RefType.applyRef[TemporalExtent](l)
    }

  type TwoDimBbox = Double :: Double :: Double :: Double :: HNil

  object TwoDimBbox {
    def apply(
        xmin: Double,
        ymin: Double,
        xmax: Double,
        ymax: Double
    ): TwoDimBbox =
      xmin :: ymin :: xmax :: ymax :: HNil
  }

  type ThreeDimBbox =
    Double :: Double :: Double :: Double :: Double :: Double :: HNil

  object ThreeDimBbox {
    def apply(
        xmin: Double,
        ymin: Double,
        zmin: Double,
        xmax: Double,
        ymax: Double,
        zmax: Double
    ): ThreeDimBbox =
      xmin :: ymin :: zmin :: xmax :: ymax :: zmax :: HNil
  }

  type Bbox = TwoDimBbox :+: ThreeDimBbox :+: CNil

  object Implicits extends CoproductInstances {

    // Stolen straight from circe docs
    implicit val decodeInstant: Decoder[Instant] = Decoder.decodeString.emap {
      str =>
        Either
          .catchNonFatal(Instant.parse(str))
          .leftMap(t => "Instant")
    }

    implicit val encodeInstant: Encoder[Instant] =
      Encoder.encodeString.contramap[Instant](_.toString)

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

    implicit val enc2DBbox: Encoder[TwoDimBbox] = new Encoder[TwoDimBbox] {
      def apply(box: TwoDimBbox) = Encoder[List[Double]].apply(box.toList)
    }

    implicit val enc3DBbox: Encoder[ThreeDimBbox] = new Encoder[ThreeDimBbox] {
      def apply(box: ThreeDimBbox) = Encoder[List[Double]].apply(box.toList)
    }

    // These `new Exception(message)` underlying exceptions aren't super helpful, but the wrapping
    // ParsingFailure should be sufficient to match on for any client that needs to do so
    implicit val dec2DBbox: Decoder[TwoDimBbox] = Decoder[List[Double]] map {
      case nums if nums.length == 4 =>
        nums(0) :: nums(1) :: nums(2) :: nums(3) :: HNil
      case nums if nums.length > 4 =>
        val message = s"Too many values for 2D bbox: $nums"
        throw new ParsingFailure(message, new Exception(message))
      case nums if nums.length < 3 =>
        val message = s"Too few values for 2D bbox: $nums"
        throw new ParsingFailure(message, new Exception(message))
    }

    implicit val dec3DBbox: Decoder[ThreeDimBbox] = Decoder[List[Double]] map {
      case nums if nums.length == 6 =>
        nums(0) :: nums(1) :: nums(2) :: nums(3) :: nums(4) :: nums(5) :: HNil
      case nums if nums.length > 6 =>
        val message = s"Too many values for 3D bbox: $nums"
        throw new ParsingFailure(message, new Exception(message))
      case nums if nums.length < 6 =>
        val message = s"Too few values for 3D bbox: $nums"
        throw new ParsingFailure(message, new Exception(message))
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
