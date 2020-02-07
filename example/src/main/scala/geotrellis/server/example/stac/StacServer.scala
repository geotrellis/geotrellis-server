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

package geotrellis.server.example.stac

import cats.data.NonEmptyList
import cats.effect.{ExitCode, IO, IOApp}
import cats.implicits._
import com.azavea.maml.error.MamlError
import com.softwaremill.sttp.asynchttpclient.cats.AsyncHttpClientCatsBackend
import org.http4s.server.blaze._
import org.http4s._
import org.http4s.server._

object StacServer extends IOApp {

  implicit val backend = AsyncHttpClientCatsBackend[IO]()
  val stacService = new StacService()

  def run(args: List[String]): IO[ExitCode] =
    BlazeServerBuilder[IO]
      .bindHttp(8080, "0.0.0.0")
      .withHttpApp(stacService.app)
      .serve
      .compile
      .drain
      .as(ExitCode.Success)
}
