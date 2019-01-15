package geotrellis.server

import com.azavea.maml.ast.{Literal, MamlKind}

import cats.effect._
import simulacrum._

@typeclass trait TmsReification[A] {
  @op("kind") def kind(self: A): MamlKind
  @op("tmsReification") def tmsReification[F[_]](self: A, buffer: Int)(implicit F: ConcurrentEffect[F]): (Int, Int, Int) => F[Literal]
}

