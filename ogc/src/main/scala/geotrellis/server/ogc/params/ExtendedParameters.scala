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

package geotrellis.server.ogc.params

import geotrellis.raster.TargetCell
import com.azavea.maml.ast.{Expression, FocalHillshade, FocalSlope}

import cats.Semigroup
import cats.data.{NonEmptyList, Validated}
import cats.data.Validated.Valid
import cats.syntax.option._
import cats.syntax.apply._
import cats.instances.option._
import cats.syntax.semigroup._

/**
 * A case class with extended parameters that can be defined for the GeoTrellis server endpoints.
 *
 * @param azimuth  azimuth for the FocalSlope and FocalHillshade configuration
 * @param altitude altitude the FocalSlope and FocalHillshade configuration
 * @param zFactor  zFactor for the FocalSlope and FocalHillshade configuration
 * @param target   the TargetCell for the Focal operation
 */
case class ExtendedParameters(azimuth: Option[Double], altitude: Option[Double], zFactor: Option[Double], target: Option[TargetCell]) {
  // override the default semigroup behavior
  implicit val doubleSemigroup: Semigroup[Double] = new Semigroup[Double] {
    def combine(l: Double, r: Double): Double = l
  }

  def bindExpression(expr: Expression): Expression = {
    def deepMap(expression: Expression, f: Expression => Expression): Expression =
      f(expression).withChildren(expression.children.map(f).map(deepMap(_, f)))

    deepMap(expr, {
      case e @ FocalHillshade(_, az, al, z, t) => e.copy(
        azimuth  = azimuth.getOrElse(az),
        altitude = altitude.getOrElse(al),
        zFactor  = z combine zFactor,
        target   = target.getOrElse(t)
      )

      case e @ FocalSlope(_, z, t) => e.copy(
        zFactor = z |+| zFactor,
        target  = target.getOrElse(t)
      )

      case e => e
    })
  }
}

object ExtendedParameters {
  def targetCell(str: String): Option[TargetCell] = str match {
    case "nodata" => TargetCell.NoData.some
    case "data"   => TargetCell.Data.some
    case "all"    => TargetCell.All.some
    case _        => None
  }

  def fromParams(params: ParamMap): Validated[NonEmptyList[ParamError], Option[ExtendedParameters]] = {
    val azimuth: Validated[NonEmptyList[ParamError], Option[Double]] = params.validatedOptionalParamDouble("azimuth")
    val altitude: Validated[NonEmptyList[ParamError], Option[Double]] = params.validatedOptionalParamDouble("altitude")
    val zFactor: Validated[NonEmptyList[ParamError], Option[Double]] = params.validatedOptionalParamDouble("zfactor")
    val target: Validated[NonEmptyList[ParamError], Option[TargetCell]] = params
      .validatedOptionalParam("target")
      .andThen { f =>
        f.flatMap(ExtendedParameters.targetCell) match {
          case Some(target) => Valid(target.some).toValidatedNel
          case None => Valid(None).toValidatedNel
        }
      }

    (azimuth, altitude, zFactor, target).mapN { case (az, al, z, t) =>
      // if at lest one of parsed params non empty
      if(List(az, al, z, t).map(_.nonEmpty).fold(false)(_ || _)) ExtendedParameters(az, al, z, t).some else None
    }
  }
}
