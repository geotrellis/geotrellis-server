package geotrellis.server.stac

import io.circe._
import io.circe.syntax._
import cats.implicits._
import geotrellis.vector.Extent

sealed trait Bbox {
  val toList: List[Double]
  val toExtent: Extent
}
case class TwoDimBbox(xmin: Double, ymin: Double, xmax: Double, ymax: Double)
  extends Bbox {
  val toList = List(xmin, ymin, xmax, ymax)
  val toExtent = Extent(xmin, ymin, xmax, ymax)
}
case class ThreeDimBbox(
    xmin: Double,
    ymin: Double,
    zmin: Double,
    xmax: Double,
    ymax: Double,
    zmax: Double) extends Bbox {
  val toList = List(xmin, ymin, zmin, xmax, ymax, zmax)
  val toExtent = Extent(xmin, ymin, xmax, ymax)
}

object TwoDimBbox {    implicit val decoderTwoDBox: Decoder[TwoDimBbox] =
  Decoder.decodeList[Double].emap {
    case twodim if twodim.length == 4 =>
      Either.right(TwoDimBbox(twodim(0), twodim(1), twodim(2), twodim(3)))
    case other =>
      Either.left(
        s"Incorrect number of values for 2d box - found ${other.length}, expected 4"
      )
  }


  implicit val encoderTwoDimBbox: Encoder[TwoDimBbox] =
    new Encoder[TwoDimBbox] {
      def apply(a: TwoDimBbox): Json = a.toList.asJson
    }

}
object ThreeDimBbox {

  implicit val decoderThreeDimBox: Decoder[ThreeDimBbox] =
    Decoder.decodeList[Double].emap {
      case threeDim if threeDim.length == 6 =>
        Either.right(
          ThreeDimBbox(
            threeDim(0),
            threeDim(1),
            threeDim(2),
            threeDim(3),
            threeDim(4),
            threeDim(5)
          )
        )
      case other =>
        Either.left(
          s"Incorrect number of values for 2d box - found ${other.length}, expected 4"
        )
    }

  implicit val encoderThreeDimBbox: Encoder[ThreeDimBbox] =
    new Encoder[ThreeDimBbox] {
      def apply(a: ThreeDimBbox): Json = a.toList.asJson
    }

}
object Bbox {

  implicit val encoderBbox: Encoder[Bbox] = new Encoder[Bbox] {
    def apply(a: Bbox): Json = a match {
      case two: TwoDimBbox     => two.asJson
      case three: ThreeDimBbox => three.asJson
    }
  }

  implicit val decoderBbox
  : Decoder[Bbox] = Decoder[TwoDimBbox].widen or Decoder[ThreeDimBbox].widen


}