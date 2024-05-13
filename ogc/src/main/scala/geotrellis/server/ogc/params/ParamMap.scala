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

import geotrellis.server.ogc._
import cats.syntax.option._
import cats.syntax.traverse._
import cats.data.{NonEmptyList, Validated, ValidatedNel}
import Validated._

import scala.util.{Failure, Success, Try}

case class ParamMap(params: Map[String, Seq[String]]) {
  private val _params: Map[String, Seq[String]] = params.map { case (k, v) => (k.toLowerCase, v) }

  def getParams(field: String): Option[List[String]] =
    _params.get(field).map(_.toList)

  /**
   * Get a field that must appear only once, otherwise error
   */
  def validatedParam(field: String): ValidatedNel[ParamError, String] =
    (getParams(field) match {
      case Some(v :: Nil) => Valid(v)
      case Some(_)        => Invalid(ParamError.RepeatedParam(field))
      case None           => Invalid(ParamError.MissingParam(field))
    }).toValidatedNel

  /**
   * Get a field that must appear only once, otherwise error
   */
  def validatedOptionalParam(field: String): ValidatedNel[ParamError, Option[String]] =
    (getParams(field) match {
      case None           => Valid(Option.empty[String])
      case Some(v :: Nil) => Valid(v.some)
      case Some(_)        => Invalid(ParamError.RepeatedParam(field))
    }).toValidatedNel

  def validatedOptionalParamDouble(field: String): ValidatedNel[ParamError, Option[Double]] =
    (getParams(field) match {
      case None => Valid(Option.empty[Double])
      case Some(v :: Nil) =>
        Try(java.lang.Double.parseDouble(v)) match {
          case Success(d) => Valid(d.some)
          case Failure(_) => Invalid(ParamError.ParseError(field, v))
        }
      case Some(_) => Invalid(ParamError.RepeatedParam(field))
    }).toValidatedNel

  def validatedOptionalParam[T](field: String, parseValue: String => Option[T]): ValidatedNel[ParamError, Option[T]] =
    (getParams(field) match {
      case Some(v :: Nil) =>
        parseValue(v) match {
          case Some(valid) => Valid(valid.some)
          case None        => Invalid(ParamError.ParseError(field, v))
        }
      case Some(_) => Invalid(ParamError.RepeatedParam(field))
      case None    => Valid(None)
    }).toValidatedNel

  /**
   * Get a field that must appear only once, parse the value successfully, otherwise error
   */
  def validatedParam[T](field: String, parseValue: String => Option[T]): ValidatedNel[ParamError, T] =
    (getParams(field) match {
      case Some(v :: Nil) =>
        parseValue(v) match {
          case Some(valid) => Valid(valid)
          case None        => Invalid(ParamError.ParseError(field, v))
        }
      case Some(_) => Invalid(ParamError.RepeatedParam(field))
      case None    => Invalid(ParamError.MissingParam(field))
    }).toValidatedNel

  /**
   * Get a field that must appear only once, and should be one of a list of values, otherwise error
   */
  def validatedParam(field: String, validValues: Set[String]): ValidatedNel[ParamError, String] =
    (getParams(field) match {
      case Some(v :: Nil) if validValues.contains(v.toLowerCase) => Valid(v.toLowerCase)
      case Some(v :: Nil)                                        => Invalid(ParamError.InvalidValue(field, v, validValues.toList))
      case Some(_)                                               => Invalid(ParamError.RepeatedParam(field))
      case None                                                  => Invalid(ParamError.MissingParam(field))
    }).toValidatedNel

  def validatedVersion(default: String): ValidatedNel[ParamError, String] =
    validatedVersion(default, Set(default))

  def validatedVersion(default: String, supportedVersions: Set[String]): ValidatedNel[ParamError, String] =
    (getParams("version") match {
      case Some(version :: Nil) if supportedVersions.contains(version) => Valid(version)
      case Some(Nil)                                                   => Valid(default)
      case Some(i :: Nil)                                              => Invalid(ParamError.InvalidValue("version", i, supportedVersions.toList))
      case None                                                        =>
        // Can send "acceptversions" instead
        getParams("acceptversions") match {
          case Some(Nil) =>
            Valid(default)
          case Some(versions :: Nil) =>
            val requestedVersions = versions.split(",")
            val intersection = requestedVersions.toSet & supportedVersions
            if (intersection.isEmpty) {
              Invalid(ParamError.NoSupportedVersionError(requestedVersions.toList, supportedVersions.toList))
            } else {
              Valid(intersection.max)
            }
          case Some(_) =>
            Invalid(ParamError.RepeatedParam("acceptversions"))
          case None =>
            // Version string is optional, reply with highest supported version if omitted
            Valid(default)
        }
    }).toValidatedNel

  def validatedOgcTimeSequence(field: String): ValidatedNel[ParamError, List[OgcTime]] =
    validatedOptionalParam(field).andThen {
      case Some(timeString) if timeString.contains("/") =>
        timeString
          .split(",")
          .map(OgcTimeInterval.parse)
          .toList
          .sequence match {
          case Success(list) => Valid(list)
          case Failure(e)    => Invalid(NonEmptyList.of(ParamError.ParseError(field, e.getMessage)))
        }
      case Some(timeString) =>
        OgcTimePositions
          .parse(timeString.split(",").toList)
          .map(_ :: Nil) match {
          case Success(list) => Valid(list)
          case Failure(e)    => Invalid(NonEmptyList.of(ParamError.ParseError(field, e.getMessage)))
        }
      case None => Valid(Nil)
    }

  def validatedOgcTime(field: String): ValidatedNel[ParamError, OgcTime] =
    validatedOgcTimeSequence(field).map(_.headOption.getOrElse(OgcTimeEmpty))
}
