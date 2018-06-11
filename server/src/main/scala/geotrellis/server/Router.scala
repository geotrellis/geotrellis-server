package geotrellis.server

import geotrellis.server.wcs.WcsService

import cats.data.Validated
import Validated._
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.ExecutionContext.Implicits.global
import scala.xml.NodeSeq


class Router extends LazyLogging {
  //def root =
  //  pathPrefix("healthcheck") {
  //    pathEndOrSingleSlash {
  //      HealthCheckRoute.root
  //    }
  //  } ~
  //  pathPrefix("wcs") {
  //    pathEndOrSingleSlash {
  //      WcsRoute.root
  //    }
  //  } ~
  //  path("kill") {
  //    Http().shutdownAllConnectionPools() andThen { case _ => system.terminate() }
  //    complete("Shutting down app")
  //  }
}

