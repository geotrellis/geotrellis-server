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
      val fs = fields.map { f => s"'${f}'" }.mkString(",")
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

  def generateErrorMessage(errors: List[ParamError]): String =
    errors.map(_.errorMessage).mkString("; ")
}