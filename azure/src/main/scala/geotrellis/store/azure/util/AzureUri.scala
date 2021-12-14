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

import java.net.URI

/**
 * Works with the following URIs: wasb://mycontainer@myaccount.blob.core.windows.net/testDir/testFile
 * wasbs://mycontainer@myaccount.blob.core.windows.net/testDir/testFile
 */

case class AzureUri(uri: URI) {
  def getContainer: String = uri.getUserInfo
  def getAccount: String   = uri.getAuthority.split("@").last.split("\\.").head
  def getPath: String = {
    val path = uri.getPath
    if (path.startsWith("/")) path.drop(1) else path
  }

  override def toString: String = uri.toString
}

object AzureUri {
  def fromURI(uri: URI): AzureUri       = AzureUri(uri)
  def fromString(uri: String): AzureUri = fromURI(new URI(uri))

  implicit def uriToAzureUri(uri: URI): AzureUri       = fromURI(uri)
  implicit def stringToAzureUri(uri: String): AzureUri = fromString(uri)
  implicit def azureUriToUri(uri: AzureUri): URI       = uri.uri
}
