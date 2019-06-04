package geotrellis.server.stac

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
      decode[T](x.asJson.noSpaces) should be(Right(x))
    }
  }

  describe("assets should round trip") {
    getPropTest[StacAsset]
  }

  describe("items should round trip") {
    getPropTest[StacItem]
  }

  describe("catalogs should round trip") {
    getPropTest[StacCatalog]
  }

  describe("collections should round trip") {
    getPropTest[StacCollection]
  }
}
