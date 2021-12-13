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

package geotrellis.server.example

import cats.effect.{Resource, Sync}
import com.typesafe.config.ConfigFactory
import pureconfig._
import pureconfig.generic.auto._
import pureconfig.module.catseffect.syntax._

case class ExampleConf(http: ExampleConf.Http, auth: ExampleConf.Auth)

object ExampleConf {
  case class Http(interface: String, port: Int)
  case class Auth(signingKey: String)

  lazy val load: ExampleConf = ConfigSource.default.loadOrThrow[ExampleConf]
  def loadF[F[_]: Sync](configPath: Option[String]): F[ExampleConf] =
    ConfigSource
      .fromConfig(ConfigFactory.load(configPath.getOrElse("application.conf")))
      .loadF[F, ExampleConf]

  def loadResourceF[F[_]: Sync](configPath: Option[String]): Resource[F, ExampleConf] = Resource.liftF(loadF[F](configPath))
}
