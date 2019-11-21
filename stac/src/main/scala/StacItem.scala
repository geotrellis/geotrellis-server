package geotrellis.server.stac

import geotrellis.server.stac.Implicits._

import cats.implicits._
import geotrellis.raster._
import geotrellis.vector._
import geotrellis.vector.io.json.GeometryFormats

import _root_.io.circe._

case class StacItem(
    id: String,
    stacVersion: String,
    stacExtensions: List[String],
    _type: String = "Feature",
    geometry: Geometry,
    bbox: TwoDimBbox,
    links: List[StacLink],
    assets: Map[String, StacAsset],
    collection: Option[String],
    properties: JsonObject
) {

  val cogUri: Option[String] = assets
    .filter(_._2._type == Some(`image/cog`))
    .values
    .headOption map { _.href }
}

object StacItem extends GeometryFormats {

  implicit val encStacItem: Encoder[StacItem] = Encoder.forProduct10(
    "id",
    "stac_version",
    "stac_extensions",
    "type",
    "geometry",
    "bbox",
    "links",
    "assets",
    "collection",
    "properties"
  )(
    item =>
      (
        item.id,
        item.stacVersion,
        item.stacExtensions,
        item._type,
        item.geometry,
        item.bbox,
        item.links,
        item.assets,
        item.collection,
        item.properties
      )
  )

  implicit val decStacItem: Decoder[StacItem] = Decoder.forProduct10(
    "id",
    "stac_version",
    "stac_extensions",
    "type",
    "geometry",
    "bbox",
    "links",
    "assets",
    "collection",
    "properties"
  )(
    (
        id: String,
        stacVersion: String,
        stacExtensions: Option[List[String]],
        _type: String,
        geometry: Geometry,
        bbox: TwoDimBbox,
        links: List[StacLink],
        assets: Map[String, StacAsset],
        collection: Option[String],
        properties: JsonObject
    ) => {
      StacItem(
        id,
        stacVersion,
        stacExtensions getOrElse List.empty,
        _type,
        geometry,
        bbox,
        links,
        assets,
        collection,
        properties
      )
    }
  )

}
