package geotrellis.server.http4s.maml

import geotrellis.server.core.maml.{MamlStore, MamlReification}
import geotrellis.server.http4s.auth.User
import MamlStore.ops._

import com.azavea.maml.util.Vars
import com.azavea.maml.ast.Expression
import com.azavea.maml.ast.codec.tree._
import org.http4s._
import org.http4s.dsl.Http4sDsl
import org.http4s.circe._
import io.circe._
import io.circe.parser._
import io.circe.syntax._
import cats._
import cats.data._
import cats.implicits._
import cats.effect._
import com.typesafe.scalalogging.LazyLogging
import geotrellis.raster._
import geotrellis.raster.render._

import scala.math._
import java.net.URI
import java.util.{UUID, NoSuchElementException}
import scala.util.Try
import scala.collection.mutable


class MamlPersistenceService[Store, Param](
  val store: Store
)(implicit t: Timer[IO],
           ms: MamlStore[Store],
           pd: Decoder[Param],
           mr: MamlReification[Param]) extends Http4sDsl[IO] with LazyLogging {

  object ParamBindings {
    def unapply(str: String): Option[Map[String, Param]] =
      decode[Map[String, Param]](str) match {
        case Right(res) => Some(res)
        case Left(_) => None
      }
  }

  implicit val expressionDecoder = jsonOf[IO, Expression]

  def routes = AuthedService[User, IO] {
    case authedReq @ POST -> Root / IdVar(key) as user =>
      (for {
         expr <- EitherT(authedReq.req.as[Expression].attempt)
         _    <- EitherT.pure[IO, Throwable](logger.info(s"Attempting to store expression (${authedReq.req.bodyAsText}) at key ($key)"))
         res  <- EitherT(store.putMaml(key, expr).attempt)
       } yield res).value flatMap {
        case Right(created) =>
          Created()
        case Left(InvalidMessageBodyFailure(_, _)) | Left(MalformedMessageBodyFailure(_, _)) =>
          authedReq.req.bodyAsText.compile.toList flatMap { reqBody =>
            BadRequest(s"""Unable to parse ${reqBody.mkString("")} as a MAML expression""")
          }
        case Left(err) =>
          logger.debug(err.toString, err)
          InternalServerError(err.toString)
      }

    case req @ GET -> Root / IdVar(key) as user=>
      logger.info(s"Attempting to retrieve expression at key ($key)")
      store.getMaml(key) flatMap {
        case Some(expr) => Ok(expr.asJson)
        case None => NotFound()
      }

    case req @ GET -> Root / IdVar(key) / "parameters" as user =>
      logger.info(s"Attempting to retrieve expression parameters at key ($key)")
      store.getMaml(key) flatMap {
        case Some(expr) => Ok(Vars.vars(expr).asJson)
        case None => NotFound()
      }

    case req @ POST -> Root / IdVar(key) / "parameters" / ParamBindings(paramMap) as user =>
      logger.info(s"Attempting to retrieve expression parameters at key ($key)")
      (for {
        expr <- store.getMaml(key)
        keys <- IO { Vars.vars(expr.get).keys }
      } yield keys).attempt flatMap {
        case Left(nse: NoSuchElementException) =>
          NotFound()
        case Right(keys) if (keys.foldLeft(true)(_ && paramMap.isDefinedAt(_))) =>
          Ok("Valid parameter map")
        case Right(keys) =>
          val unbound = keys.flatMap { k => if (!paramMap.isDefinedAt(k)) List(k) else List() }.toList
          BadRequest(s"The following keys lack bindings: ${unbound}")
      }
  }
}

