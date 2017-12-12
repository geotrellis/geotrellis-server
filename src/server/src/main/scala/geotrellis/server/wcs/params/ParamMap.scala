package geotrellis.server.wcs.params

import cats._
import cats.implicits._
import cats.data.{NonEmptyList, Validated, ValidatedNel}
import Validated._

private[params] case class ParamMap(params: Map[String, List[String]]) {
  private val _params = params.map { case (k, v) => (k.toLowerCase, v) }.toMap
  def getParam(field: String): Option[List[String]] =
    _params.get(field).map(_.map(_.toLowerCase))

  /** Get a field that must appear only once, otherwise error */
  def validatedParam(field: String): ValidatedNel[WCSParamsError, String] =
    (getParam(field) match {
      case Some(v :: Nil) => Valid(v)
      case Some(vs) => Invalid(RepeatedParam(field))
      case None => Invalid(MissingParam(field))
    }).toValidatedNel

  /** Get a field that must appear only once, parse the value successfully, otherwise error */
  def validatedParam[T](field: String, parseValue: String => Option[T]): ValidatedNel[WCSParamsError, T] =
    (getParam(field) match {
      case Some(v :: Nil) =>
        parseValue(v) match {
          case Some(valid) => Valid(valid)
          case None => Invalid(ParseError(field, v))
        }
      case Some(vs) => Invalid(RepeatedParam(field))
      case None => Invalid(MissingParam(field))
    }).toValidatedNel

  /** Get a field that must appear only once, and should be one of a list of values, otherwise error */
  def validatedParam(field: String, validValues: Set[String]): ValidatedNel[WCSParamsError, String] =
    (getParam(field) match {
      case Some(v :: Nil) if validValues.contains(v) => Valid(v)
      case Some(v :: Nil) => Invalid(InvalidValue(field, v, validValues.toList))
      case Some(vs) => Invalid(RepeatedParam(field))
      case None => Invalid(MissingParam(field))
    }).toValidatedNel

  def validatedVersion: ValidatedNel[WCSParamsError, String] =
    (getParam("version") match {
      case Some(version :: Nil) => Valid(version)
      case Some(s) => Invalid(RepeatedParam("version"))
      case None =>
        // Can send "acceptedversions" instead
        getParam("acceptedversions") match {
          case Some(versions :: Nil) =>
            Valid(versions.split(",").max)
          case Some(s) =>
            Invalid(RepeatedParam("acceptedversions"))
          case None =>
            Invalid(MissingMultiParam(Seq("acceptedversions", "version")))
        }
    }).toValidatedNel

}
