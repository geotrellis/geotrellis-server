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

package geotrellis.server.ogc

import com.azavea.maml.ast.{Clamp => MClamp, Normalize => MNormalize, Rescale => MRescale}
import geotrellis.server.ogc.RGBParameters._

import com.azavea.maml.ast.{Expression, RGB}
import geotrellis.server.ogc.params.ParamMap

import cats.instances.option._
import cats.syntax.option._
import cats.syntax.apply._

case class RGBParameters(
  clampRed: Option[Clamp],
  clampGreen: Option[Clamp],
  clampBlue: Option[Clamp],
  normalizeRed: Option[Normalize],
  normalizeGreen: Option[Normalize],
  normalizeBlue: Option[Normalize],
  rescaleRed: Option[Rescale],
  rescaleGreen: Option[Rescale],
  rescaleBlue: Option[Rescale]
) {

  def bind: Expression => Expression = {
    case e @ RGB(children, _, _, _) =>
      children match {
        case r :: g :: b :: Nil =>
          val id: Expression => Expression = identity
          val rmap                         =
            clampRed.map(_.bind).getOrElse(id) andThen
            normalizeRed.map(_.bind).getOrElse(id) andThen
            rescaleRed.map(_.bind).getOrElse(id)

          val gmap =
            clampGreen.map(_.bind).getOrElse(id) andThen
            normalizeGreen.map(_.bind).getOrElse(id) andThen
            rescaleGreen.map(_.bind).getOrElse(id)

          val bmap =
            clampBlue.map(_.bind).getOrElse(id) andThen
            normalizeBlue.map(_.bind).getOrElse(id) andThen
            rescaleBlue.map(_.bind).getOrElse(id)

          e.copy(children = rmap(r) :: gmap(g) :: bmap(b) :: Nil)
        case _                  => e
      }

    case e => e
  }
}

object RGBParameters {
  case class Clamp(min: Double, max: Double)                                           {
    def bind: Expression => Expression = {
      case e @ MClamp(_, _, _, _) => e.copy(min = min, max = max)
      case e                      => e
    }
  }
  case class Normalize(oldMin: Double, oldMax: Double, newMin: Double, newMax: Double) {
    def bind: Expression => Expression = {
      case e @ MNormalize(_, _, _, _, _, _) => e.copy(oldMin = oldMin, oldMax = oldMax, newMax = newMax, newMin = newMin)
      case e                                => e
    }
  }
  case class Rescale(newMin: Double, newMax: Double)                                   {
    def bind: Expression => Expression = {
      case e @ MRescale(_, _, _, _) => e.copy(newMax = newMax, newMin = newMin)
      case e                        => e
    }
  }

  def fromParams(params: ParamMap): Option[RGBParameters] =
    ("Red" :: "Green" :: "Blue" :: Nil).map { band =>
      val clamp =
        (
          params.validatedOptionalParamDouble(s"clampMin$band").toOption.flatten,
          params.validatedOptionalParamDouble(s"clampMax$band").toOption.flatten
        ).mapN(Clamp)

      val normalize =
        (
          params.validatedOptionalParamDouble(s"normalizeOldMin$band").toOption.flatten,
          params.validatedOptionalParamDouble(s"normalizeOldMax$band").toOption.flatten,
          params.validatedOptionalParamDouble(s"normalizeNewMin$band").toOption.flatten,
          params.validatedOptionalParamDouble(s"normalizeNewMax$band").toOption.flatten
        ).mapN(Normalize)

      val rescale =
        (
          params.validatedOptionalParamDouble(s"rescaleNewMin$band").toOption.flatten,
          params.validatedOptionalParamDouble(s"rescaleNewMax$band").toOption.flatten
        ).mapN(Rescale)

      (clamp, normalize, rescale)
    } match {
      case (rc, rn, rr) :: (gc, gn, gr) :: (bc, bn, br) :: Nil =>
        RGBParameters(rc, gc, bc, rn, gn, bn, rr, gr, br).some
      case _                                                   => None
    }

  def extendedParametersBinding: Option[ParamMap => Option[Expression => Expression]] =
    Option(fromParams(_).map(_.bind))
}
