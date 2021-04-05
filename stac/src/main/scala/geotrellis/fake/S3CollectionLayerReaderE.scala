package geotrellis.fake

import geotrellis.layer._
import geotrellis.raster.ByteArrayTile
import geotrellis.store._
import geotrellis.store.avro._
import geotrellis.store.s3.{S3AttributeStore, S3ClientProducer, S3CollectionReader, S3LayerHeader}
import geotrellis.util._
import software.amazon.awssdk.services.s3.model._
import software.amazon.awssdk.services.s3.S3Client
import io.circe._

import scala.reflect.ClassTag

/** Handles reading raster RDDs and their metadata from S3.
  *
  * @param attributeStore  AttributeStore that contains metadata for corresponding LayerId
  * @tparam K              Type of RDD Key (ex: SpatialKey)
  * @tparam V              Type of RDD Value (ex: Tile or MultibandTile )
  * @tparam M              Type of Metadata associated with the RDD[(K,V)]
  */
class S3CollectionLayerReaderE(
  val attributeStore: AttributeStore,
  s3Client: => S3Client = S3ClientProducer.get()
) extends CollectionLayerReader[LayerId] {

  def read[
    K: AvroRecordCodec: Boundable: Decoder: ClassTag,
    V: AvroRecordCodec: ClassTag,
    M: Decoder: Component[*, Bounds[K]]
  ](id: LayerId, rasterQuery: LayerQuery[K, M], filterIndexOnly: Boolean) = {
    if (!attributeStore.layerExists(id)) throw new LayerNotFoundError(id)

    val LayerAttributes(_, metadata, _, _) =
      try {
        attributeStore.readLayerAttributes[S3LayerHeader, M, K](id)
      } catch {
        case e: AttributeNotFoundError => throw new LayerReadError(id).initCause(e)
        case e: NoSuchBucketException  => throw new LayerReadError(id).initCause(e)
      }

    val queryKeyBounds = rasterQuery(metadata)
    val layerMetadata  = metadata.setComponent[Bounds[K]](queryKeyBounds.foldLeft(EmptyBounds: Bounds[K])(_ combine _))
    val tile           = ByteArrayTile.fill(0, 256, 256).asInstanceOf[V]

    val seq = queryKeyBounds.flatMap {
      case KeyBounds(min, max) =>
        val (SpatialKey(minCol, minRow), SpatialKey(maxCol, maxRow)) = min.asInstanceOf[SpatialKey] -> max.asInstanceOf[SpatialKey]
        for { c <- minCol to maxCol; r <- minRow to maxRow } yield SpatialKey(c, r).asInstanceOf[K] -> tile
      case _                   => Nil
    }

    println(s"seq.length: ${seq.length}")

    new ContextCollection(seq, layerMetadata)
  }
}

object S3CollectionLayerReaderE {
  def apply(attributeStore: AttributeStore): S3CollectionLayerReaderE =
    new S3CollectionLayerReaderE(attributeStore, S3ClientProducer.get())

  def apply(bucket: String, prefix: String, s3Client: => S3Client = S3ClientProducer.get()): S3CollectionLayerReaderE =
    apply(new S3AttributeStore(bucket, prefix, s3Client))
}
