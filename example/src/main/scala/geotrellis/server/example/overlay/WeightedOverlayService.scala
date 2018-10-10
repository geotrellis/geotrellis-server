package geotrellis.server.example.overlay

import geotrellis.server._
import geotrellis.server.example.persistence.MamlStore
import MamlStore.ops._
import TmsReification.ops._

import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap
import com.azavea.maml.error.Interpreted
import com.azavea.maml.util.Vars
import com.azavea.maml.ast._
import com.azavea.maml.ast.codec.tree._
import com.azavea.maml.eval._
import org.http4s._
import org.http4s.dsl.Http4sDsl
import org.http4s.circe._
import io.circe._
import io.circe.parser._
import io.circe.syntax._
import cats._
import cats.data._, Validated._
import cats.implicits._
import cats.effect._
import com.typesafe.scalalogging.LazyLogging
import geotrellis.raster._
import geotrellis.raster.histogram._
import geotrellis.raster.render._

import scala.math._
import java.net.URI
import java.util.{UUID, NoSuchElementException}
import scala.util.Try
import scala.collection.mutable
import scala.concurrent.ExecutionContext


class WeightedOverlayService(
  interpreter: BufferingInterpreter = BufferingInterpreter.DEFAULT
)(implicit contextShift: ContextShift[IO]) extends Http4sDsl[IO] with LazyLogging {

  // Unapply to handle UUIDs on path
  object IdVar {
    def unapply(str: String): Option[UUID] = {
      if (!str.isEmpty)
        Try(UUID.fromString(str)).toOption
      else
        None
    }
  }

  implicit val expressionDecoder = jsonOf[IO, Map[String, WeightedOverlayDefinition]]

  val demoStore: ConcurrentLinkedHashMap[UUID, Json] = new ConcurrentLinkedHashMap.Builder[UUID, Json]()
    .maximumWeightedCapacity(100)
    .build();

  val demoHistogramStore: ConcurrentLinkedHashMap[UUID, Histogram[Double]] = new ConcurrentLinkedHashMap.Builder[UUID, Histogram[Double]]()
    .maximumWeightedCapacity(100)
    .build();

  val transparentColorMap = ColorRamps.Viridis
    .map { RGBA(_).unzipRGB }
    .map { case (r, g, b) => RGBA(r, g, b, 50.0) }

  // Make the expression to evaluate based on input parameters
  def mkExpression(defs: Map[String, WeightedOverlayDefinition]): Expression = {
    def adjustedWeight(weight: Double) = if (weight == 0) 1.0 else weight * 2
    val weighted: List[Expression] = defs.map({ case(id, overlayDefinition) =>
      Multiplication(List(RasterVar(id.toString), DblLit(adjustedWeight(overlayDefinition.weight))).toList)
    }).toList

    if (weighted.length == 1)
      weighted.head
    else
      Addition(weighted)
  }

  // Grab the stored eval parameters
  def getParams(id: UUID) =
    IO { Option(demoStore.get(id)).flatMap(_.as[Map[String, WeightedOverlayDefinition]].toOption).get }
      .recoverWith({ case _: NoSuchElementException => throw MamlStore.ExpressionNotFound(id) })

  // Dead simple caching to share histograms across requests
  def histo(id: UUID): IO[Interpreted[Histogram[Double]]] = Option(demoHistogramStore.get(id)) match {
    case Some(hist) =>
      IO.pure(Valid(hist))
    case None =>
      LayerHistogram.generateExpression(mkExpression, getParams(id), interpreter, 512).map { hist =>
        hist.map { demoHistogramStore.put(id, _) }
        hist
      }
  }

  def routes: HttpRoutes[IO] = HttpRoutes.of {
    // Handle the static files for this demo
    case request @ GET -> Root =>
      StaticFile.fromResource("/overlay-demo/index.html", ExecutionContext.global, Some(request)).getOrElseF(NotFound())

    case request @ GET -> Root / path if List(".js", ".css", ".map", ".html", ".webm").exists(path.endsWith) =>
      StaticFile.fromResource("/overlay-demo/" + path, ExecutionContext.global, Some(request)).getOrElseF(NotFound())

    case req @ POST -> Root / IdVar(key) =>
      (for {
         args <- req.as[Map[String, WeightedOverlayDefinition]]
         _    <- req.bodyAsText.compile.toList flatMap { reqBody =>
                   IO.pure(logger.info(s"Attempting to store expression (${reqBody.mkString("")}) at key ($key)"))
                 }
         _    <- IO.pure { demoStore.put(key, args.asJson) }
      } yield ()).attempt flatMap {
        case Right(_) =>
          // In parallel, store histogram
          demoHistogramStore.put(key, null)
          IO.shift *> histo(key)
          Created()
        case Left(InvalidMessageBodyFailure(_, _)) | Left(MalformedMessageBodyFailure(_, _)) =>
          req.bodyAsText.compile.toList flatMap { reqBody =>
            BadRequest(s"""Unable to parse ${reqBody.mkString("")} as a MAML expression""")
          }
        case Left(err) =>
          logger.debug(err.toString, err)
          InternalServerError(err.toString)
      }

    case req @ GET -> Root / IdVar(key) / IntVar(z) / IntVar(x) / IntVar(y) ~ "png" =>

      val eval = LayerTms.generateExpression(
        mkExpression,
        getParams(key),
        interpreter
      )

      (eval(z, x, y), histo(key))
        .parMapN { (_, _) }
        .attempt
        .flatMap {
          case Right((Valid(tile), Valid(histogram))) =>
            val cmap = ColorMap.fromQuantileBreaks(histogram, transparentColorMap)
            Ok(tile.renderPng(cmap).bytes)
          case Right((Invalid(errs1), Invalid(errs2))) =>
            logger.debug(List(errs1, errs2).asJson.toString)
            BadRequest(List(errs1, errs2).asJson)
          case Right((Invalid(errs), _)) =>
            logger.debug(errs.asJson.noSpaces)
            BadRequest(errs.asJson)
          case Right((_, Invalid(errs))) =>
            logger.debug(errs.asJson.noSpaces)
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

