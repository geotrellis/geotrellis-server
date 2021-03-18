package geotrellis.stac.extensions.proj

import geotrellis.raster.Dimensions
import io.circe.{Decoder, Encoder}
import cats.syntax.either._

import scala.util.Try

case class ProjShape(cols: Long, rows: Long) {
  def toDimensions: Dimensions[Long] = Dimensions(cols, rows)
  def toList: List[Long] = List(cols, rows)
}

object ProjShape {
  def apply(list: List[Long]): Either[String, ProjShape] = Try {
    val List(cols, rows) = list.take(2)
    ProjShape(cols, rows)
  }.toEither.leftMap(_.getMessage)

  implicit val enProjShape: Encoder[ProjShape] = Encoder.encodeList[Long].contramap(_.toList)
  implicit val decProjShape: Decoder[ProjShape] = Decoder.decodeList[Long].emap(ProjShape.apply)
}