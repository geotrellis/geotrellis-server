package geotrellis.server.http4s.maml

import geotrellis.server.core.error.RequirementNotFound
import geotrellis.server.core.persistence.MamlStore
import MamlStore.ops._

import com.azavea.maml.eval.Interpreter
import org.http4s._
import org.http4s.dsl.Http4sDsl
import org.http4s.implicits._
import cats._
import cats.effect._
import cats.implicits._
import com.typesafe.scalalogging.LazyLogging
import com.typesafe.config.ConfigFactory
import io.circe._
import io.circe.syntax._
import geotrellis.raster.MultibandTile

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Try
import scala.xml.NodeSeq
import java.net.URI
import java.util.UUID
import java.lang.IllegalArgumentException


object MamlTmsService {
  type TileFn = (Int, Int, Int) => MultibandTile
}

class MamlTmsService[ExpressionStore: MamlStore](
  store: ExpressionStore,
  interpreter: BufferingInterpreter
) extends Http4sDsl[IO] with LazyLogging {

  object IdVar {
    def unapply(str: String): Option[UUID] = {
      if (!str.isEmpty)
        Try(UUID.fromString(str)).toOption
      else
        None
    }
  }

  implicit val uriQueryParamDecoder: QueryParamDecoder[UUID] =
    QueryParamDecoder[String].map(UUID.fromString)

  object OptionalNodeIdQueryParam extends OptionalQueryParamDecoderMatcher[UUID]("opacity")

  def routes: HttpService[IO] = HttpService[IO] {
    case req @ GET -> Root / IdVar(mamlId) / IntVar(z) / IntVar(x) / IntVar(y) :? OptionalNodeIdQueryParam(subNode) =>
      (for {
        expression  <- store.getMaml(mamlId).map(_.liftTo[IO](throw new RequirementNotFound(s"No maml found at id $mamlId")))
        sources     <- IO { expression.sources }
        buffered    <- tileResolver.resolveBuffered(tileSources)(z, x, y)
        reifiedExpr <- tmsInterpreter(reifiedExpr)
        tile        <- interpreted.as[Tile]
      } yield tile.renderPng(ColorRamps.Viridis).bytes).attempt flatMap {
        case Right(bytes) => Ok(bytes)
        case Left(RequirementNotFound(msg)) => NotFound(msg)
        case Left(err) => InternalServerError(err.toString)
      }
  }
}

