package geotrellis.stac.api

import com.azavea.stac4s.{StacCollection, StacItem}
import org.http4s.Method.{GET, POST}
import org.http4s.{Request, Uri}
import org.http4s.Uri.{Authority, RegName, Scheme}
import org.http4s.client.Client
import io.circe.syntax._
import org.http4s.circe._
import cats.syntax.option._
import cats.syntax.functor._
import cats.syntax.either._
import cats.effect.Sync
import eu.timepit.refined.types.string.NonEmptyString

case class Http4sStacClient[F[_]: Sync](
  client: Client[F],
  host: String = "localhost",
  port: Int = 9090
) extends StacClient[F] {
  private def postRequest: Request[F] = Request[F]().withMethod(POST)
  private def getRequest: Request[F] = Request[F]().withMethod(GET)
  private val baseUri: Uri = Uri(Scheme.http.some, Authority(host = RegName(host), port = port.some).some)

  def search(filter: SearchFilters = SearchFilters()): F[List[StacItem]] =
    client
      .expect(postRequest.withUri(baseUri.withPath("/search")).withEntity(filter.asJson))
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
