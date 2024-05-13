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
 * Works with the following URIs: wasb://${container}@${account}.blob.core.windows.net/testDir/testFile
 * wasbs://${mycontainer}@${myaccount}.blob.core.windows.net/testDir/testFile
 */

case class AzureURI(uri: URI) {
  def getContainer: String = uri.getUserInfo
  def getAccount: String = uri.getAuthority.split("@").last.split("\\.").head
  def getPath: String = {
    val path = uri.getPath
    if (path.startsWith("/")) path.drop(1) else path
  }

  override def toString: String = uri.toString
}

object AzureURI {

  /**
   * May transform the input http / https URI of the following shape: https://${account}.blob.core.windows.net/${container}/path into the format
   * compatible with the AzureURI
   */
  def fromURI(uri: URI): AzureURI =
    if (List("wasbs", "wasb") contains uri.getScheme) AzureURI(uri)
    else {
      // get URI path, remove the first slash
      val path = uri.getPath
      val npath = (if (path.startsWith("/")) path.tail else path).split("/")
      // the first item in the path is the container
      val container = npath.head
      // no container path
      val ncpath = npath.tail.mkString("/")
      AzureURI(new URI(s"wasbs://$container@${uri.getAuthority}/$ncpath"))
    }
  def fromString(uri: String): AzureURI = fromURI(new URI(uri))

  implicit def uriToAzureUri(uri: URI): AzureURI = fromURI(uri)
  implicit def stringToAzureUri(uri: String): AzureURI = fromString(uri)
  implicit def azureUriToUri(uri: AzureURI): URI = uri.uri
}
