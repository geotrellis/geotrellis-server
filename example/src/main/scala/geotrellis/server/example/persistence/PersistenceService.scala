package geotrellis.server.example.persistence

import geotrellis.server._
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
import cats.effect._
import com.typesafe.scalalogging.LazyLogging

import java.util.UUID
import scala.util.Try


class PersistenceService[Store, Param](
  val store: Store
)(implicit contextShift: ContextShift[IO],
           ms: MamlStore[Store],
           pd: Decoder[Param],
           mr: TmsReification[Param]) extends Http4sDsl[IO] with LazyLogging {

  // Unapply to handle UUIDs on path
  object IdVar {
    def unapply(str: String): Option[UUID] = {
      if (!str.isEmpty)
        Try(UUID.fromString(str)).toOption
      else
        None
    }
  }

  object ParamBindings {
    def unapply(str: String): Option[Map[String, Param]] =
      decode[Map[String, Param]](str) match {
        case Right(res) => Some(res)
        case Left(_) => None
      }
  }

  implicit val expressionDecoder = jsonOf[IO, Expression]

  def routes: HttpRoutes[IO] = HttpRoutes.of {
    case req @ POST -> Root / IdVar(key) =>
      (for {
         expr <- req.as[Expression]
         _    <- IO { logger.info(s"Attempting to store expression (${req.bodyAsText}) at key ($key)") }
         res  <- store.putMaml(key, expr)
       } yield res).attempt flatMap {
        case Right(created) =>
          Created()
        case Left(InvalidMessageBodyFailure(_, _)) | Left(MalformedMessageBodyFailure(_, _)) =>
          req.bodyAsText.compile.toList flatMap { reqBody =>
            BadRequest(s"""Unable to parse ${reqBody.mkString("")} as a MAML expression""")
          }
        case Left(err) =>
          logger.debug(err.toString, err)
          InternalServerError(err.toString)
      }

    case req @ GET -> Root / IdVar(key) =>
      logger.info(s"Attempting to retrieve expression at key ($key)")
      store.getMaml(key) flatMap {
        case Some(expr) => Ok(expr.asJson)
        case None => NotFound()
      }

    case req @ GET -> Root / IdVar(key) / "parameters" =>
      logger.info(s"Attempting to retrieve expression parameters at key ($key)")
      store.getMaml(key) flatMap {
        case Some(expr) => Ok(Vars.vars(expr).asJson)
        case None => NotFound()
      }
  }
}

