package geotrellis.server.http4s.maml

import geotrellis.server.core.cog.CogUtils
import geotrellis.server.core.maml.MamlStore
import MamlStore.ops._

import com.azavea.maml.ast._
import com.azavea.maml.ast.codec.tree._
import com.azavea.maml.eval._
import com.azavea.maml.eval.tile._
import com.azavea.maml.util.Vars
import org.http4s._
import org.http4s.dsl.Http4sDsl
import org.http4s.circe._
import io.circe._
import io.circe.syntax._
import cats._
import cats.implicits._
import cats.effect._
import cats.data.Validated._
import com.typesafe.scalalogging.LazyLogging
import geotrellis.raster._
import geotrellis.raster.render._
import geotrellis.spark.SpatialKey

import scala.math._
import java.net.URI
import java.util.{UUID, NoSuchElementException}
import scala.util.Try
import scala.collection.mutable


class SimpleMamlTmsService[Store: MamlStore](
  expressionStore: Store,
  reify: (String, Int) => (Int, Int, Int) => IO[Literal],
  interpreter: BufferingInterpreter = BufferingInterpreter.DEFAULT
)(implicit timer: Timer[IO]) extends Http4sDsl[IO] with LazyLogging {

  implicit val expressionDecoder = jsonOf[IO, Expression]

  def routes: HttpService[IO] = HttpService[IO] {
    case req @ GET -> Root / IdVar(key) / IntVar(z) / IntVar(x) / IntVar(y) =>
      (for {
        maybeExpr <- expressionStore.getMaml(key)
        expr      <- IO.pure { maybeExpr.get }.recoverWith({ case _: NoSuchElementException => throw MamlStore.ExpressionNotFound(key) })
        _         <- IO.pure { logger.info(s"Attempting to interpret expression ($expr) at key ($key)") }
        vars      <- IO.pure { Vars.varsWithBuffer(expr) }
        params    <- vars.toList.parTraverse { case (varName, (_, buffer)) =>
                       reify(varName, buffer)(z, x, y).map(varName -> _)
                     } map { _.toMap }
        reified   <- IO.pure { Expression.bindParams(expr, params) }
      } yield reified.andThen(interpreter(_)).andThen(_.as[Tile])).attempt flatMap {
        case Right(Valid(tile)) =>
          Ok(tile.renderPng(ColorRamps.Viridis).bytes)
        case Right(Invalid(errs)) =>
          BadRequest(errs.asJson)
        case Left(MamlStore.ExpressionNotFound(err)) =>
          logger.info(err.toString)
          NotFound()
        case Left(err) =>
          logger.debug(err.toString, err)
          InternalServerError(err.toString)
      }
  }
}

object SimpleMamlTmsService {
  def cogReification(varName: String, buffer: Int)(implicit t: Timer[IO]): (Int, Int, Int) => IO[Literal] =
    (zoom: Int, x: Int, y: Int) => {
    def fetch(xCoord: Int, yCoord: Int) =
      CogUtils.fetch(varName, zoom, x, y).map(_.band(1))

    (fetch(x - 1, y - 1), fetch(x, y - 1), fetch(x + 1, y - 1),
     fetch(x - 1, y),     fetch(x, y),     fetch(x + 1, y),
     fetch(x - 1, y + 1), fetch(x, y + 1), fetch(x + 1, y + 1)
    ).parMapN { (tl, tm, tr, ml, mm, mr, bl, bm, br) =>
      val extent = TileLayouts(zoom).mapTransform(SpatialKey(x, y))
      val tile = TileWithNeighbors(mm, Some(NeighboringTiles(tl, tm, tr, ml, mr, bl, bm, br))).withBuffer(buffer)
      RasterLit(Raster(tile, extent))
    }
  }
}
