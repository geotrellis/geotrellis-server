package geotrellis.server.core.persistence

import com.azavea.maml.ast.Expression
import cats._
import cats.data.EitherT
import cats.effect.IO
import simulacrum._

import java.util.UUID


@typeclass trait MamlStore[A] {
  @op("getMaml") def getMaml(store: A, key: UUID): IO[Option[Expression]]
  @op("putMaml") def putMaml(store: A, key: UUID, maml: Expression): IO[Unit]
}

