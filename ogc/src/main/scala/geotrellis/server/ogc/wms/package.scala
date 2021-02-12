/*
 * Copyright 2020 Azavea
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

package geotrellis.server.ogc

import geotrellis.server.ogc.style._
import geotrellis.server.ogc.utils._
import cats.syntax.option._
import opengis._
import opengis.wms._
import scalaxb._

import java.net.URI

import scala.xml.{Elem, NamespaceBinding, NodeSeq}

package object wms {
  val wmsScope: NamespaceBinding = scalaxb.toScope(
    Some("ogc")   -> "http://www.opengis.net/ogc",
    Some("wms")   -> "http://www.opengis.net/wms",
    Some("xlink") -> "http://www.w3.org/1999/xlink",
    Some("xs")    -> "http://www.w3.org/2001/XMLSchema",
    Some("xsi")   -> "http://www.w3.org/2001/XMLSchema-instance"
  )

  /**
    * Default scope generates an incorrect XML file (in the incorrect scope, prefixes all XML elements with `wms:` prefix.
    *
    * val defaultScope = scalaxb.toScope(Some("ogc") -> "http://www.opengis.net/ogc",
    * Some("wms") -> "http://www.opengis.net/wms",
    * Some("xlink") -> "http://www.w3.org/1999/xlink",
    * Some("xs") -> "http://www.w3.org/2001/XMLSchema",
    * Some("xsi") -> "http://www.w3.org/2001/XMLSchema-instance")
    */
  val constrainedWMSScope: NamespaceBinding = scalaxb.toScope(
    Some("ogc")   -> "http://www.opengis.net/ogc",
    Some("xlink") -> "http://www.w3.org/1999/xlink",
    Some("xs")    -> "http://www.w3.org/2001/XMLSchema",
    Some("xsi")   -> "http://www.w3.org/2001/XMLSchema-instance"
  )

  implicit class withLegendModelMethods(that: LegendModel) {
    def toLegendURL: LegendURL =
      LegendURL(
        Format = that.format,
        OnlineResource = that.onlineResource.toOnlineResource,
        attributes = Map("@width" -> DataRecord(BigInt(that.width)), "@height" -> DataRecord(BigInt(that.height)))
      )
  }

  implicit class withOnlineResourceModelMethods(that: OnlineResourceModel) {
    def toOnlineResource: OnlineResource =
      OnlineResource(
        Map(
          "@{http://www.w3.org/1999/xlink}type"    -> Option(
            DataRecord(xlink.TypeType.fromString(that.`type`, scope = toScope(Some("xlink") -> "http://www.w3.org/1999/xlink")))
          ),
          "@{http://www.w3.org/1999/xlink}href"    -> Option(DataRecord(new URI(that.href))),
          "@{http://www.w3.org/1999/xlink}role"    -> that.role.map(v => DataRecord(new URI(v))),
          "@{http://www.w3.org/1999/xlink}title"   -> that.title.map(v => DataRecord(v)),
          "@{http://www.w3.org/1999/xlink}show"    -> that.show.map(v =>
            DataRecord(xlink.ShowType.fromString(v, scope = scalaxb.toScope(Some("xlink") -> "http://www.w3.org/1999/xlink")))
          ),
          "@{http://www.w3.org/1999/xlink}actuate" -> that.actuate.map(v =>
            DataRecord(xlink.ActuateType.fromString(v, scope = scalaxb.toScope(Some("xlink") -> "http://www.w3.org/1999/xlink")))
          )
        ).collect { case (k, Some(v)) => k -> v }
      )
  }

  def ExtendedElement[A](key: String, records: DataRecord[A]*): Elem =
    DataRecord(None, key.some, records.map(_.toXML).foldLeft(NodeSeq.Empty)((acc, e) => acc ++ e)).toXML

  def ExtendedCapabilities[A](records: Elem*): List[DataRecord[Elem]] =
    DataRecord(
      DataRecord(
        None,
        "ExtendedCapabilities".some,
        DataRecord(None, "GetMap".some, records.foldLeft(NodeSeq.Empty)((acc, e) => acc ++ e)).toXML
      ).toXML
    ) :: Nil
}
