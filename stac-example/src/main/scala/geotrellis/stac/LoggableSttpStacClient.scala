package geotrellis.stac

import cats.MonadError
import cats.effect.Sync
import com.azavea.stac4s.StacCollection
import com.azavea.stac4s.api.client.{SearchFilters, SttpStacClientF}
import eu.timepit.refined.types.string.NonEmptyString
import io.circe.Encoder
import cats.syntax.functor._
import cats.syntax.flatMap._
import sttp.client3.circe.asJson
import sttp.client3.{SttpBackend, basicRequest}
import sttp.model.Uri

object LoggableSttpStacClient {

  def apply[F[_]: Sync: MonadError[*[_], Throwable]](
    client: SttpBackend[F, Any],
    baseUri: Uri
  ): LoggableSttpStacClient[F] =
    new SttpStacClientF[F](client, baseUri) {
      type Filter = SearchFilters
      protected val filterEncoder: Encoder[Filter] = SearchFilters.searchFilterEncoder

      override def collection(collectionId: NonEmptyString): F[StacCollection] = {
        Sync[F].delay {
          println(s"baseUri.withPath(collections, ${collectionId.value})")
        } >>
        client
          .send(
            basicRequest
              .get(baseUri.withPath("collections", collectionId.value))
              .response(asJson[StacCollection])
          )
          .map(_.body)
          .flatMap(MonadError[F, Throwable].fromEither)
      }
    }
    // SttpStacClientF.instance[F, SearchFilters](client, baseUri)
}
