package geotrellis.server

import com.azavea.maml.ast.{Literal, MamlKind}

import cats.effect._
import simulacrum._

@typeclass trait TmsReification[A] {
  @op("kind") def kind(self: A): MamlKind
  @op("tmsReification") def tmsReification(self: A, buffer: Int)(implicit contextShift: ContextShift[IO]): (Int, Int, Int) => IO[Literal]
}

