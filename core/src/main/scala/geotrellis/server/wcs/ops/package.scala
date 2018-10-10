package geotrellis.server.wcs

import geotrellis.spark._


package object ops {
  type MetadataCatalog = Map[String, (Seq[Int], Option[TileLayerMetadata[SpatialKey]])]
}

