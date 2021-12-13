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

package geotrellis.server.ogc.utils

import scalaxb.DataRecord

import scala.xml.XML
import scala.xml.Elem

object ScalaxbUtils {
  def toXML[A](record: DataRecord[A], dropValue: Boolean = false): Elem =
    if (!dropValue) (record.namespace, record.key, record.value) match {
      case (None, Some(k), _)    => XML.loadString(s"<$k>${record.value}</$k>")
      case (Some(n), Some(k), _) => XML.loadString(s"<$n:$k>${record.value}</$n:$k>")
      case _                     => XML.loadString(s"<${record.value}/>")
    }
    else
      (record.namespace, record.key, record.value) match {
        case (None, Some(k), _)    => XML.loadString(s"<$k/>")
        case (Some(n), Some(k), _) => XML.loadString(s"<$n:$k/>")
        case _                     => throw new IllegalArgumentException(s"The passed record $record should contain namespace or key.")
      }
}
