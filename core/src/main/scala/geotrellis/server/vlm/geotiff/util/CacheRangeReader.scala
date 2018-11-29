package geotrellis.server.vlm.geotiff.util

import geotrellis.util._

// TODO: GeoTiffRasterSources should be able to accept custom range readers?
case class CacheRangeReader(rr: RangeReader, cachedBytes: Array[Byte]) extends RangeReader {
  def totalLength: Long = rr.totalLength

  override def readRange(start: Long, length: Int): Array[Byte] = {
    val end = length + start
    if (end <= cachedBytes.length) java.util.Arrays.copyOfRange(cachedBytes, start.toInt, end.toInt)
    else rr.readRange(start, length)
  }

  protected def readClippedRange(start: Long, length: Int): Array[Byte] = rr.readRange(start, length)

  override def readAll(): Array[Byte] = rr.readAll()
}

