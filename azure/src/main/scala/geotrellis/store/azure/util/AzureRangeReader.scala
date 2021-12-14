/*
 * Copyright 2021 Azavea
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

package geotrellis.store.azure.util

import com.azure.core.util.Context
import com.azure.storage.blob.models.BlobRange
import com.azure.storage.blob.{BlobClient, BlobContainerClient, BlobServiceClient}
import geotrellis.store.azure.AzureBlobServiceClientProducer
import geotrellis.util.RangeReader

import java.io.ByteArrayOutputStream
import java.net.URI

class AzureRangeReader(uri: AzureURI, blobServiceClient: BlobServiceClient) extends RangeReader {
  @transient lazy val blobContainerClient: BlobContainerClient = blobServiceClient.getBlobContainerClient(uri.getContainer)
  @transient lazy val blobClient: BlobClient                   = blobContainerClient.getBlobClient(uri.getPath)

  val totalLength: Long = blobClient.getProperties.getBlobSize

  def readClippedRange(start: Long, length: Int): Array[Byte] = {
    val os = new ByteArrayOutputStream()
    try {
      blobClient.downloadStreamWithResponse(
        os,
        new BlobRange(start, length),
        AzureBlobServiceClientProducer.DEFAULT_RETRY_POLICY,
        null,
        false,
        null,
        Context.NONE
      )
      os.toByteArray
    } finally os.close()
  }
}

object AzureRangeReader {
  def apply(uri: AzureURI, blobServiceClient: BlobServiceClient): AzureRangeReader =
    new AzureRangeReader(uri, blobServiceClient)

  def apply(path: String, blobServiceClient: BlobServiceClient): AzureRangeReader =
    apply(new URI(path), blobServiceClient)
}
