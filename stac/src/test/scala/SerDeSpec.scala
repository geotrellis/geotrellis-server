package geotrellis.server.stac

import geotrellis.server.stac.Implicits._
import Generators._

import cats.implicits._
import geotrellis.vector.Geometry
import io.circe._
import io.circe.syntax._
import io.circe.parser._
import org.scalacheck.Arbitrary
import org.scalatest.{FunSpec, Matchers}
import org.scalatest.prop.PropertyChecks

import java.time.Instant

class SerDeSpec extends FunSpec with Matchers with PropertyChecks {
  private def getPropTest[T: Arbitrary: Encoder: Decoder] = forAll { (x: T) =>
    {
      withClue(x.asJson.spaces2) {
        decode[T](x.asJson.noSpaces).toOption.get shouldBe x
      }
    }
  }

  describe("serialization / deserialization should succeed") {
    it("enums should round trip") {
      getPropTest[StacMediaType]
      getPropTest[StacProviderRole]
    }

    it("times and geometries should round trip") {
      getPropTest[Instant]
      getPropTest[Geometry]
    }

    it("assets should round trip") {
      getPropTest[StacAsset]
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

    it("collections should round trip") {
      getPropTest[StacCollection]
    }
  }

}
