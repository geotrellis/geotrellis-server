package geotrellis.stac.extensions.proj

import io.circe.{Decoder, Encoder}
import cats.syntax.either._
import geotrellis.raster.CellSize

import scala.util.Try

case class ProjTransform(upx: Double, xres: Double, xskew: Double, upy: Double, yskew: Double, yres: Double) {
  def toArray: Array[Double] = Array(upx, xres, xskew, upy, yskew, yres)
  def toList: List[Double] = toArray.toList
  def cellSize: CellSize = CellSize(math.abs(xres), math.abs(yres))
}

object ProjTransform {
  def fromArray(transform: Array[Double]): ProjTransform = {
    val Array(upx, xres, xskew, upy, yskew, yres) = transform
    ProjTransform(upx, xres, xskew, upy, yskew, yres)
  }

  def fromList(transform: List[Double]): ProjTransform = fromArray(transform.toArray)

  def apply(transform: Array[Double]): Either[String, ProjTransform] = Try {
    fromArray(transform)
  }.toEither.leftMap(_.getMessage)

  def apply(transform: List[Double]): Either[String, ProjTransform] = apply(transform.toArray)

  implicit val enProjGDALTransform: Encoder[ProjTransform] = Encoder.encodeList[Double].contramap(_.toList)
  implicit val decProjGDALTransform: Decoder[ProjTransform] = Decoder.decodeList[Double].emap(ProjTransform.apply)
}