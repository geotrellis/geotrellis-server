package geotrellis.server

import geotrellis.server.wcs.WcsRoute

import akka.actor.ActorSystem
import akka.http.scaladsl.server._
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorMaterializer
import cats.data.Validated
import Validated._
import com.typesafe.scalalogging.LazyLogging

import scala.xml.NodeSeq

class Router(implicit val system: ActorSystem, implicit val materializer: ActorMaterializer) extends LazyLogging {
  def root =
    pathPrefix("healthcheck") {
      pathEndOrSingleSlash {
        HealthCheckRoute.root
      }
    } ~
    pathPrefix("wcs") {
      pathEndOrSingleSlash {
        WcsRoute.root
      }
    }
}
