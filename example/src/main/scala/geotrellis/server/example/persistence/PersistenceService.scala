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

import geotrellis.server._

import com.azavea.maml.util.Vars
import com.azavea.maml.ast.Expression
import com.azavea.maml.ast.codec.tree._
import org.http4s._
import org.http4s.dsl.Http4sDsl
import org.http4s.circe._
import io.chrisdavenport.log4cats.Logger
import io.circe._
import io.circe.parser._
import io.circe.syntax._
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.syntax.applicativeError._
import cats.effect._
import cats.ApplicativeError

import java.util.UUID
import scala.util.Try

class PersistenceService[F[_]: Sync: Logger: ApplicativeError[*[_], Throwable], S: MamlStore[F, *], T: TmsReification[F, *]: Decoder](
    val store: S
) extends Http4sDsl[F] {
  val logger = Logger[F]

  // Unapply to handle UUIDs on path
  object IdVar {
    def unapply(str: String): Option[UUID] =
      if (!str.isEmpty) Try(UUID.fromString(str)).toOption
      else None
  }

  object ParamBindings {
    def unapply(str: String): Option[Map[String, T]] =
      decode[Map[String, T]](str) match {
        case Right(res) => Some(res)
        case Left(_)    => None
      }
  }

  implicit val expressionDecoder = jsonOf[F, Expression]

  def routes: HttpRoutes[F] =
    HttpRoutes.of {
      case req @ POST -> Root / IdVar(key) =>
        (for {
          expr <- req.as[Expression]
          _ <- logger.info(
            s"Attempting to store expression (${req.bodyText}) at key ($key)"
          )
          res <- MamlStore[F, S].putMaml(store, key, expr)
        } yield res).attempt flatMap {
          case Right(created) =>
            Created()
          case Left(InvalidMessageBodyFailure(_, _)) | Left(MalformedMessageBodyFailure(_, _)) =>
            req.bodyText.compile.toList flatMap { reqBody =>
              BadRequest(s"""Unable to parse ${reqBody
                .mkString("")} as a MAML expression""")
            }
          case Left(err) =>
            logger.debug(err.toString)
            InternalServerError(err.toString)
        }

      case req @ GET -> Root / IdVar(key) =>
        logger.info(s"Attempting to retrieve expression at key ($key)")
        MamlStore[F, S].getMaml(store, key) flatMap {
          case Some(expr) => Ok(expr.asJson)
          case None       => NotFound()
        }

      case req @ GET -> Root / IdVar(key) / "parameters" =>
        logger.info(s"Attempting to retrieve expression parameters at key ($key)")
        MamlStore[F, S].getMaml(store, key) flatMap {
          case Some(expr) => Ok(Vars.vars(expr).asJson)
          case None       => NotFound()
        }
    }
}
