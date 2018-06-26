package geotrellis.server.http4s.tms

import org.http4s._
import org.http4s.dsl.Http4sDsl
import org.http4s.implicits._

import cats.effect._
import com.typesafe.scalalogging.LazyLogging
import com.typesafe.config.ConfigFactory
import io.circe._
import io.circe.syntax._

import geotrellis.spark.io.AttributeStore
import geotrellis.server.http4s.wcs.params._
import geotrellis.server.http4s.wcs.ops._
import geotrellis.raster.MultibandTile
import geotrellis.spark._
import geotrellis.spark.io._

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Try
import scala.xml.NodeSeq
import java.net.URI


object TmsService {
  type TileFn = (Int, Int, Int) => MultibandTile
}

class TmsService[Context](read: Context => TmsService.TileFn) extends Http4sDsl[IO] with LazyLogging {

  def routes: HttpService[IO] = HttpService[IO] {
    case req @ GET -> Root / IntVar(z) / IntVar(x) / IntVar(y) =>
      Ok("NOTHING HERE")
  }

}

