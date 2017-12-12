package geotrellis.server

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer

import scala.concurrent.ExecutionContext.Implicits.global

object AkkaSystem {
  implicit val system = ActorSystem("geotrellis-server")
  implicit val materializer = ActorMaterializer()
}

object Main extends Config {
  import AkkaSystem._

  def main(args: Array[String]): Unit = {
    val router = new Router()
    Http().bindAndHandle(router.root, httpConfig.interface, httpConfig.port)
  }
}
