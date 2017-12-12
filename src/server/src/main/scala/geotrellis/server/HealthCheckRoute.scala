package geotrellis.server

import akka.http.scaladsl.server._
import akka.http.scaladsl.server.Directives._
import com.typesafe.scalalogging.LazyLogging
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport._
import io.circe.generic.JsonCodec

@JsonCodec
case class HealthCheck(status: String = "OK")

object HealthCheckRoute extends LazyLogging {
  def root =
    get {
      complete {
        HealthCheck()
      }
    }
}
