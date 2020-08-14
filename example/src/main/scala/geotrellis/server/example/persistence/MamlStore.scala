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

package geotrellis.server.example.persistence

import com.azavea.maml.ast.{Expression, Literal}
import cats._
import cats.data.EitherT
import cats.effect.IO

import java.util.UUID

trait MamlStore[F[_], A] {
  def getMaml(self: A, key: UUID): F[Option[Expression]]
  def putMaml(self: A, key: UUID, maml: Expression): F[Unit]
}

object MamlStore {
  def apply[F[_], A](implicit ev: MamlStore[F, A]) = ev

  /**
    *  This exception should be thrown when a MAML expression can't be
    *   found in a putative MamlStore implementer
    */
  case class ExpressionNotFound(key: UUID) extends Exception(s"No expression found at $key")
}
