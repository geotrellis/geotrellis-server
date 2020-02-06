package geotrellis.server.ogc.ows

import cats.syntax.option._
import scalaxb.{CanWriteXML, DataRecord}

import scala.reflect.{ClassTag, classTag}

/** A function that reduces boilerplate by generating a namespace and a key for a common ows [[DataRecord]] */
object OwsDataRecord {
  def apply[T: CanWriteXML: ClassTag](value: T): DataRecord[T] =
    apply[T](classTag[T].toString.split("\\.").lastOption.flatMap(_.split("Type").headOption), value)

  def apply[T: CanWriteXML](key: Option[String], value: T): DataRecord[T] =
    DataRecord("ows".some, key.map(k => s"ows:$k"), value)

  def apply[T: CanWriteXML](key: String, value: T): DataRecord[T] =
    DataRecord("ows".some, s"ows:$key".some, value)
}
