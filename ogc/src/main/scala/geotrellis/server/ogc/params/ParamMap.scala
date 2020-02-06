package geotrellis.server.ogc.params

import cats.implicits._
import cats.data.{Validated, ValidatedNel}
import Validated._
import com.typesafe.scalalogging.LazyLogging

case class ParamMap(params: Map[String, Seq[String]]) extends LazyLogging {
  private val _params: Map[String, Seq[String]] = params.map { case (k, v) => (k.toLowerCase, v) }

  def getParams(field: String): Option[List[String]] =
    _params.get(field).map(_.toList)

  /** Get a field that must appear only once, otherwise error */
  def validatedParam(field: String): ValidatedNel[ParamError, String] =
    (getParams(field) match {
      case Some(v :: Nil) => Valid(v)
      case Some(vs) => Invalid(ParamError.RepeatedParam(field))
      case None => Invalid(ParamError.MissingParam(field))
    }).toValidatedNel


  /** Get a field that must appear only once, otherwise error */
  def validatedOptionalParam(field: String): ValidatedNel[ParamError, Option[String]] =
    (getParams(field) match {
      case None => Valid(Option.empty[String])
      case Some(v :: Nil) => Valid(Some(v))
      case Some(vs) => Invalid(ParamError.RepeatedParam(field))
    }).toValidatedNel

  /** Get a field that must appear only once, parse the value successfully, otherwise error */
  def validatedParam[T](field: String, parseValue: String => Option[T]): ValidatedNel[ParamError, T] =
    (getParams(field) match {
      case Some(v :: Nil) =>
        parseValue(v) match {
          case Some(valid) => Valid(valid)
          case None => Invalid(ParamError.ParseError(field, v))
        }
      case Some(vs) => Invalid(ParamError.RepeatedParam(field))
      case None => Invalid(ParamError.MissingParam(field))
    }).toValidatedNel

  /** Get a field that must appear only once, and should be one of a list of values, otherwise error */
  def validatedParam(field: String, validValues: Set[String]): ValidatedNel[ParamError, String] =
    (getParams(field) match {
      case Some(v :: Nil) if validValues.contains(v.toLowerCase) => Valid(v.toLowerCase)
      case Some(v :: Nil) => Invalid(ParamError.InvalidValue(field, v, validValues.toList))
      case Some(vs) => Invalid(ParamError.RepeatedParam(field))
      case None => Invalid(ParamError.MissingParam(field))
    }).toValidatedNel

  def validatedVersion(default: String): ValidatedNel[ParamError, String] =
    (getParams("version") match {
      case Some(Nil) => Valid(default)
      case Some(version :: Nil) => Valid(version)
      case Some(s) => Invalid(ParamError.RepeatedParam("version"))
      case None =>
        // Can send "acceptversions" instead
        getParams("acceptversions") match {
          case Some(Nil) =>
            Valid(default)
          case Some(versions :: Nil) =>
            Valid(versions.split(",").max)
          case Some(s) =>
            Invalid(ParamError.RepeatedParam("acceptversions"))
          case None =>
            // Version string is optional, reply with highest supported version if omitted
            Valid(default)
        }
    }).toValidatedNel
}