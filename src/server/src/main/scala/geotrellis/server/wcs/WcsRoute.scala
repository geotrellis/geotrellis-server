package geotrellis.server.wcs

import geotrellis.server.wcs.params._
import geotrellis.server.wcs.ops._

import akka.http.scaladsl.marshallers.xml.ScalaXmlSupport._
import akka.http.scaladsl.server._
import akka.http.scaladsl.server.Directives._
import cats.data.Validated
import Validated._
import com.typesafe.scalalogging.LazyLogging

import scala.xml.NodeSeq

object WcsRoute extends LazyLogging {
  def root =
    get {
      parameterMultiMap { params =>
        WCSParams(params) match {

          case Invalid(errors) =>
            complete {
              val msg = WCSParamsError.generateErrorMessage(errors.toList)
              s"""Error parsing parameters: ${msg}"""
            }
          case Valid(wcsParams) =>
            wcsParams match {
              case p: GetCapabilitiesWCSParams =>
                complete {
                  GetCapabilities.build(p)
                }
              case p: DescribeCoverageWCSParams =>
                complete {
                  DescribeCoverage.build(p)
                }
              case p: GetCoverageWCSParams =>
                complete {
                  GetCoverage.build(p)
                }
            }
        }
      }
    }
}
