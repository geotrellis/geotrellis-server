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

sealed abstract class ParamError {
  def errorMessage: String
}

object ParamError {
  final case class InvalidValue(field: String, value: String, validValues: List[String]) extends ParamError {
    def errorMessage =
      s"""Parameter '$field' has an invalid value of '$value'. Needs to be one of: ${validValues.mkString(",")}"""
  }

  final case class MissingParam(field: String) extends ParamError {
    def errorMessage =
      s"""Missing parameter '$field'"""
  }

  final case class MissingMultiParam(fields: Seq[String]) extends ParamError {
    def errorMessage = {
      val fs = fields.map(f => s"'${f}'").mkString(",")
      s"""Parameters must include one of [${fs}], but none found."""
    }
  }

  final case class RepeatedParam(field: String) extends ParamError {
    def errorMessage =
      s"""More than one instance of parameter '$field'"""
  }

  final case class ParseError(field: String, value: String) extends ParamError {
    def errorMessage =
      s"""Cannot parse value '$value' for parameter '$field'"""
  }

  final case class CrsParseError(crsDesc: String) extends ParamError {
    def errorMessage =
      s"""Cannot parse CRS from '$crsDesc'"""
  }

  final case class UnsupportedFormatError(format: String) extends ParamError {
    def errorMessage =
      s"""Unsupported format: '$format'"""
  }

  final case class NoSupportedVersionError(requestedVersions: List[String], supportedVersions: List[String]) extends ParamError {
    def errorMessage =
      s"""No available version in ${supportedVersions.mkString(", ")}: ${requestedVersions.mkString(", ")}"""
  }

  def generateErrorMessage(errors: List[ParamError]): String =
    errors.map(_.errorMessage).mkString("; ")
}
