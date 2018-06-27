package geotrellis.server.http4s.maml

import org.http4s._
import org.http4s.dsl.Http4sDsl
import org.http4s.implicits._

import cats.effect._
import com.typesafe.scalalogging.LazyLogging
import com.typesafe.config.ConfigFactory
import io.circe._
import io.circe.syntax._
import com.azavea.maml.eval.Interpreter

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Try
import scala.xml.NodeSeq
import java.net.URI


object MamlTmsService {
  type TileFn = (Int, Int, Int) => MultibandTile
}

class MamlTmsService[Context](interpreter: Interpreter, read: Context => TmsService.TileFn) extends Http4sDsl[IO] with LazyLogging {

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

  object OptionalNodeIdQueryParam = OptionalQueryParamDecoderMatcher[UUID]("opacity")

  def routes: HttpService[IO] = HttpService[IO] {
    case req @ GET -> Root / IdVar(mamlId) / IntVar(z) / IntVar(x) / IntVar(y)  +& OptionalNodeIdQueryParam(subNode) =>
      for {
        expression  <- ??? //getExpression
        sources     <- IO { expression.sources }
        buffered    <- tileResolver.resolveBuffered(tileSources)(z, x, y)
        reifiedExpr <- tmsInterpreter(reifiedExpr)
        tile        <- interpreted.as[Tile]
      } yield Ok(tile.renderPng(ColorRamps.Viridis).bytes)
  }
}

