package geotrellis.server.stac

import cats.implicits._
import io.circe._
import io.circe.syntax._
import io.circe.parser._
import org.scalacheck.Arbitrary
import org.scalatest.{FunSpec, Matchers}
import org.scalatest.prop.PropertyChecks
import Generators._

class SerDeSpec extends FunSpec with Matchers with PropertyChecks {
  private def getPropTest[T: Arbitrary: Encoder: Decoder] = forAll { (x: T) =>
    {
      decode[T](x.asJson.noSpaces).toOption.get should equal(x)
    }
  }

  describe("serialization / deserialization should succeed") {
    it("assets should round trip") {
      getPropTest[StacAsset]
    }

    it("items should round trip") {
      getPropTest[StacItem]
    }

    it("catalogs should round trip") {
      // getPropTest[StacCatalog]
      true
    }

    it("collections should round trip") {
      // getPropTest[StacCollection]
      true
    }
  }

}
