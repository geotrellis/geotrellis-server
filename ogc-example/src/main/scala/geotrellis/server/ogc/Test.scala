package geotrellis.server.ogc

import geotrellis.server.ogc.conf._
import geotrellis.server.ogc.wcs._

import cats.effect._


import pureconfig._
import pureconfig.generic.auto._

import scala.concurrent.ExecutionContext
import java.net.URL

object Test {
  import Conf._

  def main(args: Array[String]): Unit = {
    implicit val cs: ContextShift[IO] = IO.contextShift(ExecutionContext.global)
    implicit val timer = IO.timer(ExecutionContext.global)

    val test = for {
      conf       <- LoadConf(None).as[Conf]
    } yield {
      val simpleSources = conf
        .layers
        .values
        .collect { case ssc@SimpleSourceConf(_, _, _, _) => ssc.model }
        .toList
      val wcsModel = WcsModel(
        conf.wcs.serviceMetadata,
        conf.wcs.layerSources(simpleSources)
      )

      new CoverageView(wcsModel, new URL("http://localhost/"), Nil).toXML
    }

    test.unsafeRunSync()
  }

}
