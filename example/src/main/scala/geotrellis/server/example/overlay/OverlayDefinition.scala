package geotrellis.server.example.overlay

import geotrellis.server.core.cog.CogUtils
import geotrellis.server.core.maml.MamlReification

import com.azavea.maml.ast.{Expression, Literal, MamlKind, RasterLit}
import com.azavea.maml.eval.tile._
import io.circe._
import io.circe.syntax._
import io.circe.generic.semiauto._
import cats.effect._
import cats.syntax.all._
import geotrellis.raster._
import geotrellis.vector.Extent


case class OverlayDefinition(uri: String, band: Int, weight: Double)

object OverlayDefinition {
  implicit val olDecoder: Decoder[OverlayDefinition] = deriveDecoder
  implicit val olEncoder: Encoder[OverlayDefinition] = deriveEncoder


  implicit val overlayDefinitionReification: MamlReification[OverlayDefinition] =
    new MamlReification[OverlayDefinition] {
      def kind(self: OverlayDefinition): MamlKind = MamlKind.Tile

      def tmsReification(self: OverlayDefinition, buffer: Int)(implicit t: Timer[IO]): (Int, Int, Int) => IO[Literal] =
        (z: Int, x: Int, y: Int) => {
          CogUtils.fetch(self.uri.toString, z, x, y).map(_.band(self.band - 1)).map { tile =>
            val extent = CogUtils.tmsLevels(z).mapTransform.keyToExtent(x, y)
            RasterLit(Raster(tile, extent))
          }
        }
    }
}

