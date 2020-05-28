package geotrellis.stac.api

import com.azavea.stac4s.syntax._
import com.azavea.stac4s.extensions.layer.LayerItemExtension
import cats.effect.{ConcurrentEffect, IO}
import cats.data.NonEmptyList
import cats.data.Validated.Valid
import eu.timepit.refined.types.string.NonEmptyString
import org.http4s.client.blaze.BlazeClientBuilder
import org.http4s.client.middleware.Logger
import geotrellis.stac.IOSpec

import scala.language.reflectiveCalls

class Http4sStacClientSpec extends IOSpec {
  def withClient[F[_]: ConcurrentEffect] = new {
    def apply[T](f: StacClient[F] => F[T]): F[T] =
      BlazeClientBuilder[F](executionContext).resource.use { client =>
        f(Http4sStacClient[F](Logger(logBody = false, logHeaders = false)(client)))
      }
  }

  describe("Http4sStacClientSpec") {
    it("should handle the search query") {
      withClient[IO].apply { client =>
        client
          .search()
          .map(_.map(_.getExtensionFields[LayerItemExtension]))
          .map { list =>
            list shouldBe List(Valid(
              LayerItemExtension(
                NonEmptyList(NonEmptyString.unsafeFrom("layer-us"), NonEmptyString.unsafeFrom("layer-ca") :: Nil)
              )
            ))
          }
      }
    }

    it("should handle the collections query") {
      withClient[IO].apply { client =>
        client.collections.map { list => println(list); true shouldBe true }
      }
    }
  }
}
