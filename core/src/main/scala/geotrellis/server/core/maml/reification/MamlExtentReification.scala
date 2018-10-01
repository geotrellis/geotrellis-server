package geotrellis.server.core.maml.reification

import geotrellis.raster.CellSize
import geotrellis.vector.Extent
import com.azavea.maml.ast.{Literal, MamlKind}
import cats._
import cats.data.EitherT
import cats.effect._
import simulacrum._

import java.util.UUID


@typeclass trait MamlExtentReification[A] {
  @op("kind") def kind(self: A): MamlKind
  @op("extentReification") def extentReification(self: A)(implicit contextShift: ContextShift[IO]): (Extent, CellSize) => IO[Literal]
}

