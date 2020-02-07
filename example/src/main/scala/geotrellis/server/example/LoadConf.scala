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

import cats.effect.IO
import com.typesafe.config.ConfigFactory
import pureconfig.error.ConfigReaderException

import scala.reflect.ClassTag

object LoadConf {

  import pureconfig._

  def apply(configFile: String = "application.conf") = new {
    def as[Conf: ClassTag: ConfigReader]: IO[Conf] =
      IO {
        loadConfig[Conf](ConfigFactory.load(configFile))
      }.flatMap {
        case Right(config) => IO.pure(config)
        case Left(e) => IO.raiseError[Conf](new ConfigReaderException[Conf](e))
      }
  }
}

