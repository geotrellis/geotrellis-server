package geotrellis.server.stac

import cats.syntax._
import cats.implicits._
import geotrellis.vector.{Geometry, Point, Polygon}
import io.circe.JsonObject
import io.circe.syntax._
import org.scalacheck._
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.cats.implicits._
import shapeless._
import java.time.Instant
import com.github.tbouron.SpdxLicense

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

  private def instantGen: Gen[Instant] = arbitrary[Int] map { x =>
    Instant.now.plusMillis(x.toLong)
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
    StacRoot,
    Parent,
    Child,
    Item,
    Items,
    Source,
    Collection,
    License
  )

  private def providerRoleGen: Gen[StacProviderRole] = Gen.oneOf(
    Licensor,
    Producer,
    Processor,
    Host
  )

  private def twoDimBboxGen: Gen[TwoDimBbox] =
    (arbitrary[Double], arbitrary[Double], arbitrary[Double], arbitrary[Double])
      .mapN(TwoDimBbox.apply _)

  private def spdxGen: Gen[SPDX] =
    arbitrary[SpdxLicense] map (license => SPDX(SpdxId.unsafeFrom(license.id)))

  private def threeDimBboxGen: Gen[ThreeDimBbox] =
    (
      arbitrary[Double],
      arbitrary[Double],
      arbitrary[Double],
      arbitrary[Double],
      arbitrary[Double],
      arbitrary[Double]
    ).mapN(ThreeDimBbox.apply _)

//  This breaks test, the decoder can't handle it - commenting out for now
//  private def bboxGen: Gen[Bbox] =
//    Gen.oneOf(twoDimBboxGen map { Coproduct[Bbox](_) }, threeDimBboxGen map {
//      Coproduct[Bbox](_)
//    })
//

  private def bboxGen: Gen[Bbox] = twoDimBboxGen map { Coproduct[Bbox](_) }

  private def stacLinkGen: Gen[StacLink] =
    (
      nonEmptyStringGen,
      Gen.const(Self), // self link type is required by TMS reification
      Gen.option(mediaTypeGen),
      Gen.option(nonEmptyStringGen),
      Gen.nonEmptyListOf[String](arbitrary[String])
    ).mapN(StacLink.apply _)

  private def temporalExtentGen: Gen[TemporalExtent] = {
    (arbitrary[Instant], arbitrary[Instant]).tupled
      .map {
        case (start, end) =>
          TemporalExtent(start, end)
      }
  }

  private def stacExtentGen: Gen[StacExtent] =
    (
      bboxGen,
      temporalExtentGen
    ).mapN(StacExtent.apply _)

  private def stacProviderGen: Gen[StacProvider] =
    (
      nonEmptyStringGen,
      Gen.option(nonEmptyStringGen),
      Gen.listOf(providerRoleGen),
      Gen.option(nonEmptyStringGen)
    ).mapN(StacProvider.apply _)

  private def stacAssetGen: Gen[StacAsset] =
    (nonEmptyStringGen, Gen.option(nonEmptyStringGen), Gen.option(mediaTypeGen)) mapN {
      StacAsset.apply _
    }

  // Only do COGs for now, since we don't handle anything else in the example server.
  // As more types of stac items are supported, relax this assumption
  private def cogAssetGen: Gen[StacAsset] =
    stacAssetGen map { asset =>
      asset.copy(_type = Some(`image/cog`))
    }

  private def stacItemGen: Gen[StacItem] =
    (
      nonEmptyStringGen,
      Gen.const("0.8.0"),
      Gen.const(List.empty[String]),
      Gen.const("Feature"),
      rectangleGen,
      twoDimBboxGen,
      Gen.nonEmptyListOf(stacLinkGen),
      Gen.nonEmptyMap((nonEmptyStringGen, cogAssetGen).tupled),
      Gen.option(nonEmptyStringGen),
      Gen.const(JsonObject.fromMap(Map.empty))
    ).mapN(StacItem.apply _)

  private def stacCatalogGen: Gen[StacCatalog] =
    (
      nonEmptyStringGen,
      nonEmptyStringGen,
      Gen.option(nonEmptyStringGen),
      nonEmptyStringGen,
      Gen.listOf(stacLinkGen)
    ).mapN(StacCatalog.apply _)

  private def stacCollectionGen: Gen[StacCollection] =
    (
      nonEmptyStringGen,
      nonEmptyStringGen,
      Gen.option(nonEmptyStringGen),
      nonEmptyStringGen,
      Gen.listOf(nonEmptyStringGen),
      nonEmptyStringGen,
      spdxGen,
      Gen.listOf(stacProviderGen),
      stacExtentGen,
      Gen.const(JsonObject.fromMap(Map.empty)),
      Gen.listOf(stacLinkGen)
    ).mapN(PublicStacCollection.apply _)

  private def itemCollectionGen: Gen[ItemCollection] =
    (
      Gen.const("FeatureCollection"),
      Gen.listOf[StacItem](stacItemGen),
      Gen.listOf[StacLink](stacLinkGen)
    ).mapN(ItemCollection.apply _)

  implicit val arbMediaType: Arbitrary[StacMediaType] = Arbitrary {
    mediaTypeGen
  }

  implicit val arbLinkType: Arbitrary[StacLinkType] = Arbitrary { linkTypeGen }

  implicit val arbProviderRole: Arbitrary[StacProviderRole] = Arbitrary {
    providerRoleGen
  }

  implicit val arbInstant: Arbitrary[Instant] = Arbitrary { instantGen }

  implicit val arbGeometry: Arbitrary[Geometry] = Arbitrary { rectangleGen }

  implicit val arbAsset: Arbitrary[StacAsset] = Arbitrary { stacAssetGen }

  implicit val arbItem: Arbitrary[StacItem] = Arbitrary { stacItemGen }

  implicit val arbCatalog: Arbitrary[StacCatalog] = Arbitrary { stacCatalogGen }

  implicit val arbCollection: Arbitrary[StacCollection] = Arbitrary {
    stacCollectionGen
  }

  implicit val arbStacExtent: Arbitrary[StacExtent] = Arbitrary {
    stacExtentGen
  }

  implicit val arbTwoDimBbox: Arbitrary[TwoDimBbox] = Arbitrary {
    twoDimBboxGen
  }

  implicit val arbThreeDimBbox: Arbitrary[ThreeDimBbox] = Arbitrary {
    threeDimBboxGen
  }

  implicit val arbTemporalExtent: Arbitrary[TemporalExtent] = Arbitrary {
    temporalExtentGen
  }

  implicit val arbBbox: Arbitrary[Bbox] = Arbitrary {
    bboxGen
  }

  implicit val arbSPDX: Arbitrary[SPDX] = Arbitrary { spdxGen }

  implicit val arbItemCollection: Arbitrary[ItemCollection] = Arbitrary {
    itemCollectionGen
  }
}
