package geotrellis.server.core.maml.persistence

import com.azavea.maml.ast.{Expression, Literal}
import cats._
import cats.data.EitherT
import cats.effect.IO
import simulacrum._

import java.util.UUID


@typeclass trait MamlStore[A] {
  @op("getMaml") def getMaml(self: A, key: UUID): IO[Option[Expression]]
  @op("putMaml") def putMaml(self: A, key: UUID, maml: Expression): IO[Unit]
}

object MamlStore {
  /**
   *  This exception should be thrown when a MAML expression can't be
   *   found in a putative MamlStore implementer
   **/
  case class ExpressionNotFound(key: UUID) extends Exception(s"No expression found at $key")
}
