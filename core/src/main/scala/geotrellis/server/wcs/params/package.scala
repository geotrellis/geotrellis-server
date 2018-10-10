package geotrellis.server.wcs

import cats._
import cats.implicits._
import cats.data.{NonEmptyList, Validated, ValidatedNel}

package object params {
  private[params] implicit val nelSemigroup: Semigroup[NonEmptyList[WcsParamsError]] =
    SemigroupK[NonEmptyList].algebra[WcsParamsError]
}
