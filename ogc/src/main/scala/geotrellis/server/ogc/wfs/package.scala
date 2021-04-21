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

package geotrellis.server.ogc

import opengis.gml.GeometryPropertyType
import opengis._
import scalaxb.DataRecord

import scala.xml.{Elem, NamespaceBinding, NodeSeq, XML}

package object wfs {
  val wfsScope: NamespaceBinding = scalaxb.toScope(
    None          -> "http://www.opengis.net/wfs",
    Some("gml")   -> "http://www.opengis.net/gml",
    Some("ows")   -> "http://www.opengis.net/ows/1.1",
    Some("ogc")   -> "http://www.opengis.net/ogc",
    Some("xlink") -> "http://www.w3.org/1999/xlink",
    Some("xsi")   -> "http://www.w3.org/2001/XMLSchema-instance"
  )

  implicit class ElemOps(val elem: Elem) extends AnyVal {
    def nestedXML(key: String): Elem = XML.loadString(s"<$key>$elem</$key>")
  }

  implicit class NodeSeqOps(val elem: NodeSeq) extends AnyVal {
    def nestedXML(key: String): Elem = XML.loadString(s"<$key>$elem</$key>")
  }

  implicit class DataRecordOps(val record: DataRecord[Any]) extends AnyVal {
    def nested: Elem = nestedSeq.asInstanceOf[Elem]

    def nestedSeq: NodeSeq =
      scalaxb
        .toXML[DataRecord[Any]](
          obj = record,
          namespace = record.namespace,
          elementLabel = record.key,
          scope = wfsScope,
          typeAttribute = false
        )
  }

  implicit class GeometryPropertyTypeOps(val self: GeometryPropertyType) extends AnyVal {
    def toXML: Elem =
      scalaxb
        .toXML[GeometryPropertyType](
          obj = self,
          namespace = None,
          elementLabel = Some("Geometry"),
          scope = wfsScope,
          typeAttribute = false
        )
        .asInstanceOf[Elem]
  }
}
