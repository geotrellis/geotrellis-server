package geotrellis.server.core.wcs

import geotrellis.spark._


package object ops {
  type MetadataCatalog = Map[String, (Seq[Int], Option[TileLayerMetadata[SpatialKey]])]
}

