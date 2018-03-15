package geotrellis.server.wcs.params

sealed abstract class WCSParamsError { def errorMessage: String }

final case class InvalidValue(field: String, value: String, validValues: List[String]) extends WCSParamsError {
  def errorMessage =
    s"""Parameter '$field' has an invalid value of '$value'. Needs to be one of: ${validValues.mkString(",")}"""
}

final case class MissingParam(field: String) extends WCSParamsError {
  def errorMessage =
    s"""Missing parameter '$field.'"""
}

final case class MissingMultiParam(fields: Seq[String]) extends WCSParamsError {
  def errorMessage = {
    val fs = fields.map { f => s"'${f}'" }.mkString(",")
    s"""Parameters must include one of [${fs}], but none found."""
  }
}


final case class RepeatedParam(field: String) extends WCSParamsError {
  def errorMessage =
    s"""More than one instance of parameter '$field.'"""
}

final case class ParseError(field: String, value: String) extends WCSParamsError {
  def errorMessage =
    s"""Cannot parse value '$value' for parameter '$field.'"""
}

final case class CrsParseError(crsDesc: String) extends WCSParamsError {
  def errorMessage =
    s"""Cannot parse CRS from '$crsDesc'"""
}

final case class UnsupportedFormatError(format: String) extends WCSParamsError {
  def errorMessage =
    s"""Unsupported format: '$format'"""
}

object WCSParamsError {
  def generateErrorMessage(errors: List[WCSParamsError]): String =
    errors.map(_.errorMessage).mkString(",")
}
