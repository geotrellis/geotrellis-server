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

package geotrellis.server.ogc.stac.util.logging

import cats.effect.Sync
import com.azavea.stac4s.api.client.{ETag, SearchFilters, StreamingStacClient, StreamingStacClientF}
import com.azavea.stac4s.{StacCollection, StacItem}
import eu.timepit.refined.types.string.NonEmptyString
import fs2.Stream
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import io.circe.Json
import io.circe.syntax._
import tofu.higherKind.Mid

final class StreamingStacClientLoggingMid[F[_]: Sync] extends StreamingStacClientF[F, Mid[Stream[F, *], *], SearchFilters] {
  val logger = Slf4jLogger.getLoggerFromClass(this.getClass)

  def search: Mid[Stream[F, *], StacItem] =
    res =>
      Stream.eval(logger.trace("streaming search all endpoint call")) >>
        res.evalTap(item => logger.trace(s"retrieved item: ${item.asJson}"))

  def search(filter: SearchFilters): Mid[Stream[F, *], StacItem] =
    res =>
      Stream.eval(logger.trace(s"streaming search ${filter.asJson} endpoint call")) >>
        res.evalTap(item => logger.trace(s"retrieved item: ${item.asJson}"))

  def search(filter: Option[SearchFilters]): Mid[Stream[F, *], StacItem] =
    res =>
      Stream.eval(logger.trace(s"streaming search ${filter.asJson} endpoint call")) >>
        res.evalTap(item => logger.trace(s"retrieved item: ${item.asJson}"))

  def collections: Mid[Stream[F, *], StacCollection] =
    res =>
      Stream.eval(logger.trace("collections all endpoint call")) >>
        res.evalTap(item => logger.trace(s"retrieved collection: ${item.asJson}"))

  def items(collectionId: NonEmptyString): Mid[Stream[F, *], StacItem] =
    res =>
      Stream.eval(logger.trace(s"items by collectionId: $collectionId endpoint call")) >>
        res.evalTap(item => logger.trace(s"retrieved item: ${item.asJson}"))

  def collection(collectionId: NonEmptyString): F[StacCollection] = ???

  def collectionCreate(collection: StacCollection): F[StacCollection] = ???

  def item(collectionId: NonEmptyString, itemId: NonEmptyString): F[ETag[StacItem]] = ???

  def itemCreate(collectionId: NonEmptyString, item: StacItem): F[ETag[StacItem]] = ???

  def itemUpdate(collectionId: NonEmptyString, item: ETag[StacItem]): F[ETag[StacItem]] = ???

  def itemPatch(collectionId: NonEmptyString, itemId: NonEmptyString, patch: ETag[Json]): F[ETag[StacItem]] = ???

  def itemDelete(collectionId: NonEmptyString, itemId: NonEmptyString): F[Either[String, String]] = ???
}

object StreamingStacClientLoggingMid {
  def apply[F[_]: Sync]: StreamingStacClient[F, Mid[Stream[F, *], *]] = new StreamingStacClientLoggingMid[F]
}
