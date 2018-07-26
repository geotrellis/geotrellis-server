package geotrellis.server.core.maml.metadata

import geotrellis.vector.Extent
import com.azavea.maml.ast.{Literal, MamlKind}
import cats._
import cats.data.EitherT
import cats.effect._
import simulacrum._

import java.util.UUID


@typeclass trait MamlExtension[A] {
  @op("extent") def extent(self: A)(implicit t: Timer[IO]): IO[Extent]
}

