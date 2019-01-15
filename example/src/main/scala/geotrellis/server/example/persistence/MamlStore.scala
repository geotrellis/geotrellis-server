package geotrellis.server.example.persistence

import com.azavea.maml.ast.{Expression, Literal}
import cats._
import cats.implicits._
import cats.effect._
import simulacrum._

import java.util.UUID


@typeclass trait MamlStore[A] {
  @op("getMaml") def getMaml[F[_]](self: A, key: UUID)(implicit F: ConcurrentEffect[F]): F[Option[Expression]]
  @op("putMaml") def putMaml[F[_]](self: A, key: UUID, maml: Expression)(implicit F: ConcurrentEffect[F]): F[Unit]
}

object MamlStore {
  /**
   *  This exception should be thrown when a MAML expression can't be
   *   found in a putative MamlStore implementer
   **/
  case class ExpressionNotFound(key: UUID) extends Exception(s"No expression found at $key")
}
