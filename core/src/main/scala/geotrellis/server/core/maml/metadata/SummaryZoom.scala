package geotrellis.server.core.maml.metadata

import geotrellis.vector.Extent
import geotrellis.raster.CellSize
import com.azavea.maml.ast.{Literal, MamlKind}
import cats._
import cats.data.EitherT
import cats.effect._
import simulacrum._

import java.util.UUID


@typeclass trait SummaryZoom[A] {
  @op("maxAcceptableCellsize") def maxAcceptableCellsize(self: A, maxCells: Int)(implicit t: Timer[IO]): IO[(Extent, CellSize)]
}

