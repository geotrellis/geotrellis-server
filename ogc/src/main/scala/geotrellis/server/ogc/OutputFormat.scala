package geotrellis.server.ogc

import scala.util.Try
import geotrellis.raster.render.png._
import geotrellis.raster._
import geotrellis.raster.render._

sealed trait OutputFormat

object OutputFormat {
  case object GeoTiff extends OutputFormat { override def toString = "image/geotiff" }
  case object Jpg extends OutputFormat { override def toString = "image/jpg" }

  object Png {
    final val PngEncodingRx = """image/png(?:;encoding=(\w+))?""".r

    def encodoingToString(enc: PngColorEncoding): String = enc match {
      case RgbaPngEncoding => "rgba"
      case GreyaPngEncoding => "greya"
      case _: RgbPngEncoding => "rgb"
      case _: GreyPngEncoding => "grey"
      case _ => ???
    }

    def stringToEncodding(enc: String): Option[PngColorEncoding] = {
      Option(enc) map {
        case "rgba" => RgbaPngEncoding
        case "greya" => GreyaPngEncoding
        case "rgb" => RgbPngEncoding(None)
        case "grey" => GreyPngEncoding(None)
      }
    }
  }

  case class Png(encoding: Option[PngColorEncoding]) extends OutputFormat {
    override def toString = encoding match {
      case Some(e) => s"image/png;encoding=${Png.encodoingToString(e)}"
      case None => "image/png"
    }

    def render(tile: Tile): Array[Byte] = {
      encoding match {
        case None =>
          val nd = noDataValue(tile.cellType)
          tile.renderPng(GreyPngEncoding(nd)).bytes

        case Some(GreyPngEncoding(None)) =>
          val nd = noDataValue(tile.cellType)
          tile.renderPng(GreyPngEncoding(nd)).bytes

        case Some(RgbPngEncoding(None)) =>
          val nd = noDataValue(tile.cellType)
          tile.renderPng(RgbPngEncoding(nd)).bytes

        case Some(encoding) =>
          tile.renderPng(encoding).bytes
      }
    }

    def render(tile: Tile, cm: ColorMap) = {
      val encoder = encoding match {
        case None =>
          new PngEncoder(Settings(RgbaPngEncoding, PaethFilter))

        case Some(GreyPngEncoding(None)) =>
          val nd = noDataValue(tile.cellType)
          new PngEncoder(Settings(GreyPngEncoding(nd), PaethFilter))

        case Some(RgbPngEncoding(None)) =>
          val nd = noDataValue(tile.cellType)
          new PngEncoder(Settings(RgbPngEncoding(nd), PaethFilter))

        case Some(encoding) =>
          new PngEncoder(Settings(encoding, PaethFilter))
      }

      encoder.writeByteArray(cm.render(tile))
    }

    private def noDataValue(ct: CellType): Option[Int] = ct match {
      case ct: HasNoData[_] => Try(ct.noDataValue.asInstanceOf[Number].intValue).toOption
      case _ => None
    }
  }

  def fromStringUnsafe(str: String) = str match {
    case "geotiff" | "geotif" | "image/geotiff" =>
      OutputFormat.GeoTiff

    case Png.PngEncodingRx(enc) if List("rgb", "rgba", "grey", "greya", null) contains (enc) =>
      val encoding = Png.stringToEncodding(enc)
      OutputFormat.Png(encoding)

    case "image/jpeg" =>
      OutputFormat.Jpg
  }

  def fromString(str: String) = Try(fromStringUnsafe(str)).toOption
}
