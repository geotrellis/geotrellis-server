package geotrellis.server.ogc.wms

import cats.syntax.option._
import io.circe.Encoder
import io.circe.syntax._
import opengis.ogc.{ServiceExceptionReport, ServiceExceptionType}
import opengis._
import scalaxb._

import scala.xml.Elem

sealed trait GetFeatureInfoException extends java.lang.Exception {
  def msg: String
  def code: String
  def version: String

  def toXML: Elem =
    scalaxb
      .toXML[ServiceExceptionReport](
        obj = ServiceExceptionReport(
          ServiceException = ServiceExceptionType(msg, Map(
            "@code" -> DataRecord(code),
            "@locator" -> DataRecord("noLocator")
          )) :: Nil,
          Map("@version" -> DataRecord(version))
        ),
        namespace = None,
        elementLabel = "ServiceExceptionReport".some,
        scope = wmsScope,
        typeAttribute = false
      )
      .asInstanceOf[scala.xml.Elem]
}

object GetFeatureInfoException {
  implicit def invalidPointExceptionEncoder[T <: GetFeatureInfoException]: Encoder[T] =
    Encoder.encodeJson.contramap { e =>
      Map(
        "version" -> e.version.asJson,
        "exceptions" -> List(
          "code" -> e.code,
          "locator" -> "noLocator",
          "text" -> e.msg
        ).asJson
      ).asJson
    }
}

case class LayerNotDefinedException(msg: String, version: String) extends GetFeatureInfoException {
  val code: String = "LayerNotDefined"
}

case class InvalidPointException(msg: String, version: String) extends GetFeatureInfoException {
  val code: String = "InvalidPoint"
}
