package geotrellis.server.stac

import cats.implicits._
import geotrellis.vector.{Geometry, Point, Polygon}
import io.circe.JsonObject
import io.circe.syntax._
import org.scalacheck._
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.cats.implicits._
import shapeless._

object Generators {
  private def nonEmptyStringGen: Gen[String] =
    Gen.listOfN(30, Gen.alphaChar) map { _.mkString }

  private def rectangleGen: Gen[Geometry] =
    for {
      lowerX <- Gen.choose(0, 1000)
      lowerY <- Gen.choose(0, 1000)
    } yield {
      Polygon(
        Point(lowerX, lowerY),
        Point(lowerX + 100, lowerY),
        Point(lowerX + 100, lowerY + 100),
        Point(lowerX, lowerY + 100),
        Point(lowerX, lowerY)
      )
    }

  private def mediaTypeGen: Gen[StacMediaType] = Gen.oneOf(
    `image/tiff`,
    `image/vnd.stac.geotiff`,
    `image/cog`,
    `image/jp2`,
    `image/png`,
    `image/jpeg`,
    `text/xml`,
    `application/xml`,
    `application/json`,
    `text/plain`,
    `application/geo+json`,
    `application/geopackage+sqlite3`,
    `application/x-hdf5`,
    `application/x-hdf`
  )

  private def linkTypeGen: Gen[StacLinkType] = Gen.oneOf(
    Self,
    Root,
    Parent,
    Child,
    Item
  )

  private def twoDimBboxGen: Gen[TwoDimBbox] =
    (arbitrary[Double], arbitrary[Double], arbitrary[Double], arbitrary[Double])
      .mapN(TwoDimBbox.apply _)

  private def threeDimBboxGen: Gen[ThreeDimBbox] =
    (
      arbitrary[Double],
      arbitrary[Double],
      arbitrary[Double],
      arbitrary[Double],
      arbitrary[Double],
      arbitrary[Double]
    ).mapN(ThreeDimBbox.apply _)

  private def bboxGen: Gen[Bbox] =
    Gen.oneOf(twoDimBboxGen map { Coproduct[Bbox](_) }, threeDimBboxGen map {
      Coproduct[Bbox](_)
    })

  private def stacLinkGen: Gen[StacLink] =
    (
      nonEmptyStringGen,
      linkTypeGen,
      Gen.option(mediaTypeGen),
      Gen.option(nonEmptyStringGen)
    ).mapN(StacLink.apply _)

  private def stacAssetGen: Gen[StacAsset] =
    (nonEmptyStringGen, Gen.option(nonEmptyStringGen), Gen.option(mediaTypeGen)) mapN {
      StacAsset.apply _
    }

  private def stacItemGen: Gen[StacItem] =
    (
      nonEmptyStringGen,
      Gen.const("Feature"),
      rectangleGen,
      twoDimBboxGen,
      Gen.nonEmptyListOf(stacLinkGen),
      Gen.nonEmptyMap((nonEmptyStringGen, stacAssetGen).tupled),
      Gen.option(nonEmptyStringGen),
      Gen.const(JsonObject.fromMap(Map.empty))
    ).mapN(StacItem.apply _)

  implicit val arbAsset: Arbitrary[StacAsset] = Arbitrary { stacAssetGen }

  implicit val arbItem: Arbitrary[StacItem] = Arbitrary { stacItemGen }
}
