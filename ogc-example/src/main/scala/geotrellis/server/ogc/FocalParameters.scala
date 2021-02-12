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

import geotrellis.server.ogc.params.{ParamError, ParamMap}
import geotrellis.server.ogc.utils.ExpressionUtils

import geotrellis.raster.TargetCell
import com.azavea.maml.ast.{Expression, FocalHillshade, FocalSlope}
import cats.data.{NonEmptyList, Validated}
import cats.data.Validated.Valid
import cats.syntax.option._
import cats.syntax.apply._

case class FocalParameters(azimuth: Option[Double], altitude: Option[Double], zFactor: Option[Double], target: Option[TargetCell]) {
  private def zFactorCombine(old: Option[Double]): Option[Double] =
    (old, zFactor) match {
      case (_, n @ Some(_)) => n
      case (o, _)           => o
    }

  def bind: Expression => Expression = {
    case e @ FocalHillshade(_, az, al, z, t) =>
      e.copy(
        azimuth = azimuth.getOrElse(az),
        altitude = altitude.getOrElse(al),
        zFactor = zFactorCombine(z),
        target = target.getOrElse(t)
      )
    case e @ FocalSlope(_, z, t)             =>
      e.copy(
        zFactor = zFactorCombine(z),
        target = target.getOrElse(t)
      )
    case e                                   => e
  }
}

object FocalParameters {
  def fromParams(params: ParamMap): Validated[NonEmptyList[ParamError], Option[FocalParameters]] = {
    val azimuth: Validated[NonEmptyList[ParamError], Option[Double]]    = params.validatedOptionalParamDouble("azimuth")
    val altitude: Validated[NonEmptyList[ParamError], Option[Double]]   = params.validatedOptionalParamDouble("altitude")
    val zFactor: Validated[NonEmptyList[ParamError], Option[Double]]    = params.validatedOptionalParamDouble("zfactor")
    val target: Validated[NonEmptyList[ParamError], Option[TargetCell]] = params
      .validatedOptionalParam("target")
      .andThen { f =>
        f.flatMap(ExpressionUtils.targetCell) match {
          case Some(target) => Valid(target.some).toValidatedNel
          case None         => Valid(None).toValidatedNel
        }
      }

    (azimuth, altitude, zFactor, target).mapN {
      case (az, al, z, t) =>
        // if at lest one of parsed params non empty
        if (List(az, al, z, t).map(_.nonEmpty).fold(false)(_ || _)) FocalParameters(az, al, z, t).some else None
    }
  }

  def extendedParametersBinding: Option[ParamMap => Option[Expression => Expression]] =
    Option(fromParams(_).toOption.flatten.map(_.bind))
}
