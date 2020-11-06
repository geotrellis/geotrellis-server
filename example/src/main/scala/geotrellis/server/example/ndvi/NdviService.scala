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

package geotrellis.server.example.ndvi

import geotrellis.server._
import geotrellis.raster.{io => _, _}

import com.azavea.maml.ast._
import com.azavea.maml.ast.codec.tree._
import com.azavea.maml.eval._

import org.http4s._
import org.http4s.dsl.Http4sDsl
import org.http4s.circe._
import io.circe._
import io.circe.parser._
import io.circe.syntax._
import io.chrisdavenport.log4cats.Logger
import cats.data._
import Validated._
import cats._
import cats.effect._
import cats.implicits._

import java.net.URLDecoder

class NdviService[F[_]: Sync: Logger: Parallel, T: Encoder: Decoder: TmsReification[F, *]](
  interpreter: Interpreter[F]
) extends Http4sDsl[F] {
  val logger = Logger[F]

  object ParamBindings {
    def unapply(str: String): Option[Map[String, T]] =
      decode[Map[String, T]](str) match {
        case Right(res) => Some(res)
        case Left(_)    => None
      }
  }

  implicit val redQueryParamDecoder: QueryParamDecoder[T] =
    QueryParamDecoder[String].map { str => decode[T](URLDecoder.decode(str, "UTF-8")).right.get }
  object RedQueryParamMatcher extends QueryParamDecoderMatcher[T]("red")
  object NirQueryParamMatcher extends QueryParamDecoderMatcher[T]("nir")

  implicit val expressionDecoder = jsonOf[F, Expression]

  final val ndvi: Expression =
    Division(
      List(
        Subtraction(List(RasterVar("red"), RasterVar("nir"))),
        Addition(List(RasterVar("red"), RasterVar("nir")))
      )
    )

  final val eval = LayerTms.curried(ndvi, interpreter)

  // http://0.0.0.0:9000/{z}/{x}/{y}.png?red=geotiffnodeojson&nir=geotiffnodejson
  def routes: HttpRoutes[F] =
    HttpRoutes.of {
      // Matching json in the query parameter is a bad idea.
      case req @ GET -> Root / IntVar(z) / IntVar(x) / IntVar(y) ~ "png" :? RedQueryParamMatcher(
            red
          ) +& NirQueryParamMatcher(nir) =>
        val paramMap = Map("red" -> red, "nir" -> nir)

        eval(paramMap, z, x, y).attempt flatMap {
          case Right(Valid(mbtile)) =>
            // Image results have multiple bands. We need to pick one
            Ok(mbtile.band(0).renderPng(ColorRamps.Viridis).bytes)
          case Right(Invalid(errs)) =>
            logger.debug(errs.toList.toString) *>
            BadRequest(errs.asJson)
          case Left(err)            =>
            logger.debug(err.toString) *>
            InternalServerError(err.toString)
        }
    }
}
