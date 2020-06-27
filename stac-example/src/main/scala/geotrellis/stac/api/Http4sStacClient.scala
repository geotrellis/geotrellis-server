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

package geotrellis.stac.api

import com.azavea.stac4s.{StacCollection, StacItem}
import org.http4s.Method.{GET, POST}
import org.http4s.{Request, Uri}
import org.http4s.client.Client
import io.circe.syntax._
import org.http4s.circe._
import cats.syntax.functor._
import cats.syntax.either._
import cats.syntax.apply._
import cats.effect.{ConcurrentEffect, Resource, Sync}
import eu.timepit.refined.types.string.NonEmptyString
import org.http4s.client.blaze.BlazeClientBuilder
import io.chrisdavenport.log4cats.Logger

import scala.concurrent.ExecutionContext

case class Http4sStacClient[F[_]: Sync: Logger](
  client: Client[F],
  baseUri: Uri
) extends StacClient[F] {
  private lazy val logger = Logger[F]
  private def postRequest = Request[F]().withMethod(POST)
  private def getRequest  = Request[F]().withMethod(GET)

  def search(filter: SearchFilters = SearchFilters()): F[List[StacItem]] =
    logger.trace(s"search: ${filter.asJson.spaces4}") *>
    client
      .expect(postRequest.withUri(baseUri.withPath("/search")).withEntity(filter.asJson.noSpaces))
      .map(_.hcursor.downField("features").as[List[StacItem]].bimap(_ => Nil, identity).merge)

  def collections: F[List[StacCollection]] =
    logger.trace("collections") *>
    client
      .expect(getRequest.withUri(baseUri.withPath("/collections")))
      .map(_.hcursor.downField("collections").as[List[StacCollection]].bimap(_ => Nil, identity).merge)

  def collection(collectionId: NonEmptyString): F[Option[StacCollection]] =
    logger.trace(s"collection: $collectionId") *>
    client
      .expect(getRequest.withUri(baseUri.withPath(s"/collections/$collectionId")))
      .map(_.as[Option[StacCollection]].bimap(_ => None, identity).merge)

  def items(collectionId: NonEmptyString): F[List[StacItem]] =
    logger.trace(s"items: $collectionId") *>
    client
      .expect(getRequest.withUri(baseUri.withPath(s"/collections/$collectionId/items")))
      .map(_.hcursor.downField("features").as[List[StacItem]].bimap(_ => Nil, identity).merge)

  def item(collectionId: NonEmptyString, itemId: NonEmptyString): F[Option[StacItem]] =
    logger.trace(s"items: $collectionId, $itemId") *>
    client
      .expect(getRequest.withUri(baseUri.withPath(s"/collections/$collectionId/items/$itemId")))
      .map(_.as[Option[StacItem]].bimap(_ => None, identity).merge)
}

object Http4sStacClient {
  def apply[F[_]: ConcurrentEffect: Logger](baseUri: Uri)(implicit ec: ExecutionContext): F[Http4sStacClient[F]] = {
    BlazeClientBuilder[F](ec).resource.use { client =>
      ConcurrentEffect[F].delay(Http4sStacClient[F](client, baseUri))
    }
  }

  def resource[F[_]: ConcurrentEffect: Logger](baseUri: Uri)(implicit ec: ExecutionContext): Resource[F, Http4sStacClient[F]] = {
    BlazeClientBuilder[F](ec).resource.map { client =>
      Http4sStacClient[F](client, baseUri)
    }
  }
}
