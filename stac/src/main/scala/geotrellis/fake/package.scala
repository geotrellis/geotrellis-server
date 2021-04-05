package geotrellis

import geotrellis.layer.{SpaceTimeKey, SpatialKey, TileLayerMetadata}
import geotrellis.store.{AttributeStore, LayerHeader, LayerId}

package object fake {
  implicit class AttributeStoreOps(attributeStore: AttributeStore) {
    def readTileLayerMetadataErased(layerId: LayerId): TileLayerMetadata[_] = {
      val header = attributeStore.readHeader[LayerHeader](layerId)
      if (header.keyClass.contains("SpatialKey")) attributeStore.readMetadata[TileLayerMetadata[SpatialKey]](layerId)
      else attributeStore.readMetadata[TileLayerMetadata[SpaceTimeKey]](layerId)
    }
  }
}
