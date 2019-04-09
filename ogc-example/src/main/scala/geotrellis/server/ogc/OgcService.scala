package geotrellis.server.ogc

import geotrellis.server.ogc.wcs._
import geotrellis.server.ogc.wms._
import geotrellis.server.ogc.wmts._

import org.http4s._
import org.http4s.dsl.Http4sDsl
import cats.implicits._
import cats.effect._
import com.typesafe.scalalogging.LazyLogging

import java.net.URL

class OgcService(
  wmsModel: WmsModel,
  wcsModel: WcsModel,
  wmtsModel: WmtsModel,
  serviceUrl: URL
)(implicit contextShift: ContextShift[IO]) extends Http4sDsl[IO] with LazyLogging {

  val wcsView = new WcsView(wcsModel, serviceUrl)
  val wmsView = new WmsView(wmsModel, serviceUrl)
  val wmtsView = new WmtsView(wmtsModel, serviceUrl)

  // Predicates for choosing a service
  def isWcsReq(key: String, value: String) =
    key.toLowerCase == "service" && value.toLowerCase == "wcs"

  def isWmsReq(key: String, value: String) =
    key.toLowerCase == "service" && value.toLowerCase == "wms"

  def isWmtsReq(key: String, value: String) =
    key.toLowerCase == "service" && value.toLowerCase == "wmts"

  def routes: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> Root if req.params.exists((isWcsReq _).tupled) =>
      wcsView.responseFor(req)
    case req @ GET -> Root if req.params.exists((isWmsReq _).tupled) =>
      wmsView.responseFor(req)
    case req @ GET -> Root if req.params.exists((isWmtsReq _).tupled) =>
      wmtsView.responseFor(req)
    case req =>
      logger.warn(s"""Recv'd UNHANDLED request: $req""")
      NotFound()
  }
}
