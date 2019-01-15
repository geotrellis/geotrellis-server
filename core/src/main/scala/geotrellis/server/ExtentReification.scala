package geotrellis.server

import geotrellis.raster.CellSize
import geotrellis.vector.Extent
import com.azavea.maml.ast.{Literal, MamlKind}
import cats._
import cats.data.EitherT
import cats.effect._
import simulacrum._

import java.util.UUID


@typeclass trait ExtentReification[A] {
  @op("kind") def kind(self: A): MamlKind
  @op("extentReification") def extentReification[F[_]](self: A)(implicit F: ConcurrentEffect[F]): (Extent, CellSize) => F[Literal]
}

