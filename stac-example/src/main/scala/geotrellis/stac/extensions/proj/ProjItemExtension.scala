package geotrellis.stac.extensions.proj

import geotrellis.stac._

import geotrellis.vector.{io => _, _}
import com.azavea.stac4s.extensions.{ItemAssetExtension, ItemExtension}
import cats.kernel.Eq
import io.circe.generic.extras.{ConfiguredJsonCodec, JsonKey}

@ConfiguredJsonCodec
case class ProjItemExtension(
  @JsonKey("proj:epsg") epsgCode: Option[Int],
  @JsonKey("proj:wkt2") wktString: Option[String],
  @JsonKey("proj:geometry") geometry: Option[Geometry],
  @JsonKey("proj:transform") transform: Option[ProjTransform],
  @JsonKey("proj:shape") shape: Option[ProjShape]
)

object ProjItemExtension {
  implicit val eq: Eq[ProjItemExtension] = Eq.fromUniversalEquals

  implicit lazy val itemExtension: ItemExtension[ProjItemExtension] = ItemExtension.instance
  implicit lazy val itemAssetExtension: ItemAssetExtension[ProjItemExtension] = ItemAssetExtension.instance
}
