package geotrellis.server.wcs.params

import cats._
import cats.implicits._
import cats.data.{NonEmptyList, Validated, ValidatedNel}
import Validated._
import geotrellis.proj4.LatLng
import geotrellis.vector.Extent
import org.scalatest._

import scala.reflect.ClassTag

class WCSParamsSpec extends FunSpec with Matchers {
  def validateAs[T <: WCSParams: ClassTag](paramMap: Map[String, List[String]]): T = {
    val params = WCSParams(paramMap)
    params should be (an[Valid[_]])
    val p = params.toOption.get
    p should be (an[T])
    p.asInstanceOf[T]
  }

  describe("WCSParams parseing GetCapabilities requests") {
    it("should parse a GetCapabilities request") {
      val paramMap =
        Map(
          "Service" -> List("wcs"),
          "Request" -> List("GetCapabilities"),
          "Version" -> List("1.1.0")
        )

      val params = validateAs[GetCapabilitiesWCSParams](paramMap)
      params.version should be ("1.1.0")
    }

    it("should parse a GetCapabilities request with AcceptedVersions") {
      val paramMap =
        Map(
          "Service" -> List("wcs"),
          "Request" -> List("GetCapabilities"),
          "AcceptedVersions" -> List("1.0.0,1.1.0,1.3.0")
        )

      val params = validateAs[GetCapabilitiesWCSParams](paramMap)
      params.version should be ("1.3.0")
    }
  }

  describe("WCSParams parsing DescribeCoverage request") {
    it("should parse a 1.1.0 DescribeCoverage request") {
      val paramMap =
        Map(
          "Service" -> List("wcs"),
          "Request" -> List("DescribeCoverage"),
          "Version" -> List("1.1.0"),
          "identifiers" -> List("a,b,c")
        )

      val params = validateAs[DescribeCoverageWCSParams](paramMap)
      params.version should be ("1.1.0")
      params.identifiers.sorted.toSeq should be (Seq("a", "b", "c"))
    }

    it("should parse a 1.0.0 DescribeCoverage request") {
      val paramMap =
        Map(
          "Service" -> List("wcs"),
          "Request" -> List("DescribeCoverage"),
          "Version" -> List("1.0.0"),
          "coverage" -> List("a")
        )

      val params = validateAs[DescribeCoverageWCSParams](paramMap)
      params.version should be ("1.0.0")
      params.identifiers.sorted.toSeq should be (Seq("a"))
    }
  }

  describe("WCSParams parsing GetCoverage request") {
    it("should parse a 1.1.0 DescribeCoverage request") {
      val paramMap =
        Map(
          "Service" -> List("wcs"),
          "Request" -> List("GetCoverage"),
          "Version" -> List("1.1.0"),
          "identifier" -> List("layer"),
          "BoundingBox" -> List("0.0,1.0,1.0,2.0"),
          "crs" -> List("urn:ogc:def:crs:EPSG::4326"),
          "width" -> List("500"),
          "height" -> List("600"),
          "format" -> List("geotif")
        )

      val params = validateAs[GetCoverageWCSParams](paramMap)
      params.version should be ("1.1.0")
      params.identifier should be ("layer")
      params.crs should be (LatLng)
      params.boundingBox should be (Extent(0.0, 1.0, 1.0, 2.0))
      (params.width, params.height) should be ((500, 600))
      params.format should be ("geotiff")
    }

    it("should parse a 1.0.0 DescribeCoverage request") {
      val paramMap =
        Map(
          "Service" -> List("wcs"),
          "Request" -> List("GetCoverage"),
          "Version" -> List("1.0.0"),
          "coverage" -> List("layer"),
          "bbox" -> List("0.0,1.0,1.0,2.0"),
          "crs" -> List("urn:ogc:def:crs:EPSG::4326"),
          "width" -> List("500"),
          "height" -> List("600"),
          "format" -> List("geotif")
        )

      val params = validateAs[GetCoverageWCSParams](paramMap)
      params.version should be ("1.0.0")
      params.identifier should be ("layer")
      params.crs should be (LatLng)
      params.boundingBox should be (Extent(0.0, 1.0, 1.0, 2.0))
      (params.width, params.height) should be ((500, 600))
      params.format should be ("geotiff")
    }

    it("should parse a 1.1.0 DescribeCoverage request with crs in bbox") {
      val paramMap =
        Map(
          "Service" -> List("wcs"),
          "Request" -> List("GetCoverage"),
          "Version" -> List("1.1.0"),
          "identifier" -> List("layer"),
          "BoundingBox" -> List("0.0,1.0,1.0,2.0,urn:ogc:def:crs:EPSG::4326"),
          "width" -> List("500"),
          "height" -> List("600"),
          "format" -> List("geotif")
        )

      val params = validateAs[GetCoverageWCSParams](paramMap)
      params.version should be ("1.1.0")
      params.identifier should be ("layer")
      params.crs should be (LatLng)
      params.boundingBox should be (Extent(0.0, 1.0, 1.0, 2.0))
      (params.width, params.height) should be ((500, 600))
      params.format should be ("geotiff")
    }
  }
}
