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

package geotrellis.server.stac

import io.circe._
import io.circe.syntax._
import cats.implicits._
import geotrellis.vector.Extent

import scala.util.Try

sealed trait Bbox {
  val xmin: Double
  val ymin: Double
  val xmax: Double
  val ymax: Double
  val toList: List[Double]
  val toExtent: Either[Throwable, Extent] = try {
    Either.right(Extent(xmin, ymin, xmax, ymax))
  } catch {
    case e: Throwable => Either.left(e)
  }
}

case class TwoDimBbox(xmin: Double, ymin: Double, xmax: Double, ymax: Double)
    extends Bbox {
  val toList = List(xmin, ymin, xmax, ymax)
}

case class ThreeDimBbox(
    xmin: Double,
    ymin: Double,
    zmin: Double,
    xmax: Double,
    ymax: Double,
    zmax: Double
) extends Bbox {
  val toList = List(xmin, ymin, zmin, xmax, ymax, zmax)
}

object TwoDimBbox {
  implicit val decoderTwoDBox: Decoder[TwoDimBbox] =
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
