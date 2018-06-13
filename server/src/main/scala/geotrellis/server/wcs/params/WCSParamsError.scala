package geotrellis.server.wcs.params

sealed abstract class WcsParamsError { def errorMessage: String }

final case class InvalidValue(field: String, value: String, validValues: List[String]) extends WcsParamsError {
  def errorMessage =
    s"""Parameter '$field' has an invalid value of '$value'. Needs to be one of: ${validValues.mkString(",")}"""
}

final case class MissingParam(field: String) extends WcsParamsError {
  def errorMessage =
    s"""Missing parameter '$field.'"""
}

final case class MissingMultiParam(fields: Seq[String]) extends WcsParamsError {
  def errorMessage = {
    val fs = fields.map { f => s"'${f}'" }.mkString(",")
    s"""Parameters must include one of [${fs}], but none found."""
  }
}


final case class RepeatedParam(field: String) extends WcsParamsError {
  def errorMessage =
    s"""More than one instance of parameter '$field.'"""
}

final case class ParseError(field: String, value: String) extends WcsParamsError {
  def errorMessage =
    s"""Cannot parse value '$value' for parameter '$field.'"""
}

final case class CrsParseError(crsDesc: String) extends WcsParamsError {
  def errorMessage =
    s"""Cannot parse CRS from '$crsDesc'"""
}

final case class UnsupportedFormatError(format: String) extends WcsParamsError {
  def errorMessage =
    s"""Unsupported format: '$format'"""
}

object WcsParamsError {
  def generateErrorMessage(errors: List[WcsParamsError]): String =
    errors.map(_.errorMessage).mkString(",")
}
