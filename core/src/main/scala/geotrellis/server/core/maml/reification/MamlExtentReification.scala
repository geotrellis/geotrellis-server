package geotrellis.server.core.maml

import geotrellis.vector.Extent
import com.azavea.maml.ast.{Literal, MamlKind}
import cats._
import cats.data.EitherT
import cats.effect._
import simulacrum._

import java.util.UUID


@typeclass trait MamlExtentReification[A] {
  @op("kind") def kind(self: A): MamlKind
  @op("extentReification") def extentReification(self: A)(implicit t: Timer[IO]): Extent => IO[Literal]
}

