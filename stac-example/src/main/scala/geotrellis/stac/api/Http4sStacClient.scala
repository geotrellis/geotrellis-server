package geotrellis.stac.api

import com.azavea.stac4s.{StacCollection, StacItem}
import org.http4s.Method.{GET, POST}
import org.http4s.{Request, Uri}
import org.http4s.client.Client
import io.circe.syntax._
import org.http4s.circe._
import cats.syntax.functor._
import cats.syntax.either._
import cats.effect.{ConcurrentEffect, Resource, Sync}
import eu.timepit.refined.types.string.NonEmptyString
import org.http4s.client.blaze.BlazeClientBuilder

import scala.concurrent.ExecutionContext

case class Http4sStacClient[F[_]: Sync](
  client: Client[F],
  baseUri: Uri
) extends StacClient[F] {
  private def postRequest: Request[F] = Request[F]().withMethod(POST)
  private def getRequest: Request[F] = Request[F]().withMethod(GET)

  def search(filter: SearchFilters = SearchFilters()): F[List[StacItem]] =
    client
      .expect(postRequest.withUri(baseUri.withPath("/search")).withEntity(filter.asJson.noSpaces))
      .map(_.hcursor.downField("features").as[List[StacItem]].bimap(_ => Nil, identity).merge)

  def collections: F[List[StacCollection]] =
    client
      .expect(getRequest.withUri(baseUri.withPath("/collections")))
      .map(_.hcursor.downField("collections").as[List[StacCollection]].bimap(_ => Nil, identity).merge)

  def collection(collectionId: NonEmptyString): F[Option[StacCollection]] =
    client
      .expect(getRequest.withUri(baseUri.withPath(s"/collections/$collectionId")))
      .map(_.as[Option[StacCollection]].bimap(_ => None, identity).merge)

  def items(collectionId: NonEmptyString): F[List[StacItem]] =
    client
      .expect(getRequest.withUri(baseUri.withPath(s"/collections/$collectionId/items")))
      .map(_.hcursor.downField("features").as[List[StacItem]].bimap(_ => Nil, identity).merge)

  def item(collectionId: NonEmptyString, itemId: NonEmptyString): F[Option[StacItem]] =
    client
      .expect(getRequest.withUri(baseUri.withPath(s"/collections/$collectionId/items/$itemId")))
      .map(_.as[Option[StacItem]].bimap(_ => None, identity).merge)
}

object Http4sStacClient {
  def apply[F[_]: ConcurrentEffect](baseUri: Uri)(implicit ec: ExecutionContext): F[Http4sStacClient[F]] = {
    BlazeClientBuilder[F](ec).resource.use { client =>
      ConcurrentEffect[F].delay(Http4sStacClient[F](client, baseUri))
    }
  }

  def resource[F[_]: ConcurrentEffect](baseUri: Uri)(implicit ec: ExecutionContext): Resource[F, Http4sStacClient[F]] = {
    BlazeClientBuilder[F](ec).resource.map { client =>
      Http4sStacClient[F](client, baseUri)
    }
  }
}
