/*
 * Copyright 2021 Azavea
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package geotrellis.server.ogc.wms

import cats.syntax.option._
import geotrellis.server.ogc.InfoFormat
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
          ServiceException = ServiceExceptionType(
            msg,
            Map(
              "@code"    -> DataRecord(code),
              "@locator" -> DataRecord("noLocator")
            )
          ) :: Nil,
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
        "version"    -> e.version.asJson,
        "exceptions" -> List(
          "code"    -> e.code,
          "locator" -> "noLocator",
          "text"    -> e.msg
        ).asJson
      ).asJson
    }

  implicit class GetFeatureInfoExceptionOps[T <: GetFeatureInfoException](self: T) {
    def render(infoFormat: InfoFormat): String =
      infoFormat match {
        case InfoFormat.Json => self.asJson.noSpaces
        case InfoFormat.XML  => self.toXML.toString
      }
  }
}

case class LayerNotDefinedException(msg: String, version: String) extends GetFeatureInfoException {
  val code: String = "LayerNotDefined"
}

case class InvalidPointException(msg: String, version: String) extends GetFeatureInfoException {
  val code: String = "InvalidPoint"
}
