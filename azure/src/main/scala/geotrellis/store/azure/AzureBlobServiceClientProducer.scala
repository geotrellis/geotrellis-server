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

package geotrellis.store.azure

import com.azure.storage.blob.models.DownloadRetryOptions
import com.azure.storage.blob.{BlobServiceClient, BlobServiceClientBuilder}
import geotrellis.store.azure.conf.AzureConfig

object AzureBlobServiceClientProducer {
  @transient lazy val DEFAULT_RETRY_POLICY: DownloadRetryOptions = new DownloadRetryOptions().setMaxRetryRequests(5)

  @transient private lazy val client: BlobServiceClient =
    new BlobServiceClientBuilder().connectionString(AzureConfig.storageConnectionString).buildClient()

  private var summonClient: () => BlobServiceClient = () => client

  /**
   * Set an alternative default function for summoning BlobServiceClient
   */
  def set(getClient: () => BlobServiceClient): Unit = summonClient = getClient

  /**
   * Get the current function registered as default for summoning BlobServiceClients
   */
  def get: () => BlobServiceClient = summonClient
}
