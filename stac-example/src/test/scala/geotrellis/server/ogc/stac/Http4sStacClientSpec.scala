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

package geotrellis.server.ogc.stac

import cats.data.NonEmptyList
import cats.data.Validated.Valid
import cats.effect.{Blocker, ConcurrentEffect, ContextShift, IO}
import com.azavea.stac4s.api.client.SttpStacClient
import com.azavea.stac4s.extensions.layer.LayerItemExtension
import com.azavea.stac4s.syntax._
import eu.timepit.refined.types.string.NonEmptyString
import geotrellis.IOSpec
import geotrellis.proj4.CRS
import geotrellis.server.ogc.stac
import geotrellis.store.query.vector.ProjectedGeometry
import geotrellis.vector.Extent
import io.chrisdavenport.log4cats.{Logger => Logger4Cats}
import sttp.client3.UriContext
import sttp.client3.http4s.Http4sBackend

import scala.concurrent.ExecutionContext
import scala.language.reflectiveCalls

class Http4sStacClientSpec extends IOSpec {
  def withClient[F[_]: ContextShift: ConcurrentEffect: Logger4Cats](implicit ec: ExecutionContext) =
    new {
      def apply[T](f: SttpStacClient[F] => F[T]): F[T] =
        Http4sBackend.usingDefaultBlazeClientBuilder[F](Blocker.liftExecutionContext(executionContext), executionContext).use { client =>
          f(SttpStacClient(client, uri"http://localhost:9090/"))
        }
    }

  describe("Http4sStacClientSpec") {
    ignore("should handle the search query") {
      withClient[IO].apply { client =>
        client.search
          .take(30)
          .compile
          .toList
          .map(_.map(_.getExtensionFields[LayerItemExtension]))
          .map { list =>
            list shouldBe List(
              Valid(LayerItemExtension(NonEmptyList(NonEmptyString.unsafeFrom("layer-us"), NonEmptyString.unsafeFrom("layer-ca") :: Nil)))
            )
          }
      }
    }

    ignore("should handle the collections query") {
      withClient[IO].apply { client =>
        client.collections.take(30).compile.toList.map { list => println(list); true shouldBe true }
      }
    }

    ignore("repository") {
      import geotrellis.store.query._
      withClient[IO].apply { client =>
        stac
          .StacRepository[IO](client)
          .find {
            and(
              withName("layer-us"),
              intersects(
                ProjectedGeometry(
                  Extent(73.99023507871507, 50.266557802094795, 73.99281921403175, 50.267377974106765),
                  CRS.fromEpsgCode(4326)
                )
              )
            )
          }
          .map { list =>
            println(list)
            true shouldBe true
          }
      }
    }
  }
}
