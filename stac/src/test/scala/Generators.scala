package geotrellis.server.stac

import cats.implicits._
import org.scalacheck._
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.cats.implicits._
import shapeless._

object Generators {
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

  private def stacAssetGen: Gen[StacAsset] =
    (arbitrary[String], arbitrary[Option[String]], Gen.option(mediaTypeGen)) mapN {
      StacAsset.apply _
    }

  implicit val arbAsset: Arbitrary[StacAsset] = Arbitrary { stacAssetGen }
}
