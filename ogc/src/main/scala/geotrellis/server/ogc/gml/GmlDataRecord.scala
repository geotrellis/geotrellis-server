package geotrellis.server.ogc.gml

import cats.syntax.option._
import scalaxb.{CanWriteXML, DataRecord}

import scala.reflect.{ClassTag, classTag}

object GmlDataRecord {
  def apply[T: CanWriteXML: ClassTag](value: T): DataRecord[T] =
    apply[T](classTag[T].toString.split("\\.").lastOption.flatMap(_.split("Type").headOption), value)

  def apply[T: CanWriteXML](key: Option[String], value: T): DataRecord[T] =
    DataRecord("gml".some, key.map(k => s"gml:$k"), value)

  def apply[T: CanWriteXML](key: String, value: T): DataRecord[T] =
    DataRecord("gml".some, s"gml:$key".some, value)
}
