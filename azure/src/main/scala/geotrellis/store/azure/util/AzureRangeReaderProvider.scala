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

import geotrellis.store.azure.{AzureBlobServiceClientProducer, SCHEMES}
import geotrellis.util.RangeReaderProvider

import java.net.URI

class AzureRangeReaderProvider extends RangeReaderProvider {
  def canProcess(uri: URI): Boolean = (uri.getScheme match {
    case str: String => SCHEMES contains str.toLowerCase
    case null        => false
  }) || uri.getAuthority.endsWith(".blob.core.windows.net")

  def rangeReader(uri: URI): AzureRangeReader = AzureRangeReader(AzureURI(uri), AzureBlobServiceClientProducer.get())
}
