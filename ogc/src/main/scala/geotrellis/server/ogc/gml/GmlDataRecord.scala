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

package geotrellis.server.ogc.gml

import cats.syntax.option._
import scalaxb.{CanWriteXML, DataRecord}

import scala.reflect.{classTag, ClassTag}

object GmlDataRecord {
  def apply[T: CanWriteXML: ClassTag](value: T): DataRecord[T] =
    apply[T](classTag[T].toString.split("\\.").lastOption.flatMap(_.split("Type").headOption), value)

  def propertyType[T: CanWriteXML: ClassTag](value: T): DataRecord[T] =
    apply[T](classTag[T].toString.split("\\.").lastOption.flatMap(_.split("PropertyType").headOption), value)

  def apply[T: CanWriteXML](key: Option[String], value: T): DataRecord[T] =
    DataRecord("gml".some, key.map(k => s"gml:$k"), value)

  def apply[T: CanWriteXML](key: String, value: T): DataRecord[T] =
    DataRecord("gml".some, s"gml:$key".some, value)
}
