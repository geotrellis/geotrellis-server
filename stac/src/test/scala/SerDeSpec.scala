package geotrellis.server.stac

import geotrellis.server.stac.Implicits._
import Generators._
import geotrellis.vector.Geometry
import io.circe._
import io.circe.syntax._
import io.circe.parser._
import org.scalacheck.Arbitrary

import org.scalatest.{FunSpec, Matchers}
import org.scalatest.prop.PropertyChecks
import java.time.Instant

import com.typesafe.scalalogging.LazyLogging

class SerDeSpec
    extends FunSpec
    with Matchers
    with PropertyChecks
    with LazyLogging {
  private def getPropTest[T: Arbitrary: Encoder: Decoder] = forAll { (x: T) =>
    {
      withClue(x.asJson.spaces2) {
        decode[T](x.asJson.noSpaces) shouldBe Right(x)
      }
    }
  }

  describe("serialization / deserialization should succeed") {
    it("enums should round trip") {
      getPropTest[StacMediaType]
      getPropTest[StacLinkType]
      getPropTest[StacProviderRole]
    }

    it("times and geometries should round trip") {
      getPropTest[Instant]
      getPropTest[Geometry]
    }

    it("assets should round trip") {
      getPropTest[StacAsset]
    }

    it("SPDX should round trip") {
      getPropTest[SPDX]
    }

    it("items should round trip") {
      getPropTest[StacItem]
    }

    it("item collections should round trip") {
      getPropTest[ItemCollection]
    }

    it("catalogs should round trip") {
      getPropTest[StacCatalog]
    }

    it("two dimensional bbox should round trip") {
      getPropTest[TwoDimBbox]
    }

    it("three dimensional bbox should round trip") {
      getPropTest[ThreeDimBbox]
    }

    it("stac extents should round trip") {
      getPropTest[TemporalExtent]
      getPropTest[Bbox]
      getPropTest[StacExtent]
    }

    it("collections should round trip") {
      getPropTest[StacCollection]
    }
  }

  it("should ignore optional fields") {
    val link = decode[StacLink]("""{"href":"s3://foo/item.json","rel":"item"}""")
    link map { _.labelExtAssets } shouldBe Right(List.empty[String])
  }
}
