package geotrellis.server.ogc.wcs.params

import cats._
import cats.implicits._
import cats.data.{Validated, ValidatedNel}
import Validated._
import com.typesafe.scalalogging.LazyLogging

private[params] case class ParamMap(params: Map[String, Seq[String]]) extends LazyLogging {
  private val _params = params.map { case (k, v) => (k.toLowerCase, v) }
  logger.debug("ParamMap._params", _params)
  def getParams(field: String): Option[List[String]] =
    _params.get(field).map(_.map(_.toLowerCase).toList)

  /** Get a field that must appear only once, otherwise error */
  def validatedParam(field: String): ValidatedNel[WcsParamsError, String] =
    (getParams(field) match {
      case Some(v :: Nil) => Valid(v)
      case Some(vs) => Invalid(RepeatedParam(field))
      case None => Invalid(MissingParam(field))
    }).toValidatedNel

  /** Get a field that must appear only once, parse the value successfully, otherwise error */
  def validatedParam[T](field: String, parseValue: String => Option[T]): ValidatedNel[WcsParamsError, T] =
    (getParams(field) match {
      case Some(v :: Nil) =>
        parseValue(v) match {
          case Some(valid) => Valid(valid)
          case None => Invalid(ParseError(field, v))
        }
      case Some(vs) => Invalid(RepeatedParam(field))
      case None => Invalid(MissingParam(field))
    }).toValidatedNel

  /** Get a field that must appear only once, and should be one of a list of values, otherwise error */
  def validatedParam(field: String, validValues: Set[String]): ValidatedNel[WcsParamsError, String] =
    (getParams(field) match {
      case Some(v :: Nil) if validValues.contains(v) => Valid(v)
      case Some(v :: Nil) => Invalid(InvalidValue(field, v, validValues.toList))
      case Some(vs) => Invalid(RepeatedParam(field))
      case None => Invalid(MissingParam(field))
    }).toValidatedNel

  def validatedVersion: ValidatedNel[WcsParamsError, String] =
    (getParams("version") match {
      case Some(version :: Nil) => Valid(version)
      case Some(s) => Invalid(RepeatedParam("version"))
      case None =>
        // Can send "acceptversions" instead
        getParams("acceptversions") match {
          case Some(versions :: Nil) =>
            Valid(versions.split(",").max)
          case Some(s) =>
            Invalid(RepeatedParam("acceptversions"))
          case None =>
            // Version string is optional, reply with highest supported version if omitted
            Valid("1.1.1")
        }
    }).toValidatedNel

}
