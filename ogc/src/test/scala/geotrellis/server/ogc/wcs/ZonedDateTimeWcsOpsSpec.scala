package geotrellis.server.ogc.wcs

import org.scalatest.FunSpec
import java.time.{Instant, ZoneOffset, ZonedDateTime}

class ZonedDateTimeWcsOpsSpec extends FunSpec {
  describe("WCS extensions for ZonedDateTime") {
    it("should write an ISO string formatted based on WCS spec") {
      val zdt = ZonedDateTime.ofInstant(Instant.ofEpochSecond(1000000), ZoneOffset.UTC)
      assert(zdt.toWcsIsoString == "1970-01-12T13:46:40Z")
    }
  }
}
