package geotrellis.server.core.maml

import geotrellis.vector.Extent
import com.azavea.maml.ast.{Literal, MamlKind}
import cats._
import cats.data.EitherT
import cats.effect._
import simulacrum._

import java.util.UUID


@typeclass trait MamlTmsReification[A] {
  @op("kind") def kind(self: A): MamlKind
  @op("tmsReification") def tmsReification(self: A, buffer: Int)(implicit t: Timer[IO]): (Int, Int, Int) => IO[Literal]
}

