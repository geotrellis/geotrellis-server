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

import geotrellis.util.{FileRangeReader, RangeReader}
import geotrellis.store.s3.util.S3RangeReader
import geotrellis.util.HttpRangeReader
import geotrellis.store.s3.AmazonS3URI
import geotrellis.store.s3.S3ClientProducer

import cats.effect.IO
import org.apache.http.client.utils.URLEncodedUtils

import java.nio.file.Paths
import java.nio.charset.Charset
import java.net.URI
import java.net.URL

object RangeReaderUtils {

  def fromUri(uri: String): IO[RangeReader] =
    IO {
      val javaUri = new URI(uri)

      /**
       * Links can be signed for instance via HMAC-SHA, it means that request signature can be specific at least to the METHOD (GET and HEAD requests
       * would have different auth signature)
       *
       * AWS S3 would return 403 as each METHOD has a different signature, see:
       * https://docs.aws.amazon.com/AmazonS3/latest/API/sig-v4-header-based-auth.html
       *
       * In all cases there are some query params force GET method usage
       */
      val noQueryParams =
        URLEncodedUtils.parse(uri, Charset.forName("UTF-8")).isEmpty

      javaUri.getScheme match {
        case "file" | null =>
          FileRangeReader(Paths.get(javaUri).toFile)

        case "http" | "https" if noQueryParams =>
          HttpRangeReader(new URL(uri))

        case "http" | "https" =>
          new HttpRangeReader(new URL(uri), false)

        case "s3" =>
          val s3Uri = new AmazonS3URI(java.net.URLDecoder.decode(uri, "UTF-8"))
          val s3Client = S3ClientProducer.get()
          S3RangeReader(s3Uri.getBucket, s3Uri.getKey, s3Client)

        case scheme =>
          throw new java.lang.IllegalArgumentException(
            s"Unrecognized scheme found for range reader: $scheme"
          )
      }
    }
}
