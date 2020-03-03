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

import com.azavea.maml.ast.Expression

import cats.effect.IO
import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap

import java.util.UUID

package object persistence {
  type HashMapMamlStore = ConcurrentLinkedHashMap[UUID, Expression]
  implicit val inMemMamlStore: MamlStore[ConcurrentLinkedHashMap[UUID, Expression]] =
    new MamlStore[ConcurrentLinkedHashMap[UUID, Expression]] {
      def getMaml(self: ConcurrentLinkedHashMap[UUID, Expression], key: UUID): IO[Option[Expression]] =
        IO { Option(self.get(key)) }

      def putMaml(self: ConcurrentLinkedHashMap[UUID, Expression], key: UUID, maml: Expression): IO[Unit] =
        IO { self.put(key, maml) }
    }

}

