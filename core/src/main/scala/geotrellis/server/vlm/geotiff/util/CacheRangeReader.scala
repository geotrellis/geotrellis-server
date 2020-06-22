/*
 * Copyright 2020 Azavea
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
