package geotrellis.server.ogc.conf

import geotrellis.server.ogc.wms.source._

import geotrellis.contrib.vlm.RasterSource
import com.azavea.maml.ast._
import cats._
import cats.implicits._

import java.net.{InetAddress, URL}

// This sumtype corresponds to the in-config representation of a WMS layer
sealed trait LayerSourceConf {
  def name: String
  def styles: List[StyleConf]
}

case class SimpleLayerSource(
  name: String,
  title: String,
  source: RasterSourceConf,
  styles: List[StyleConf]
) extends LayerSourceConf {
  def model: SimpleWmsSource =
    SimpleWmsSource(name, title, source.toRasterSource, styles.map(_.model))
}

case class MapAlgebraLayerSource(
  name: String,
  title: String,
  algebra: Expression,
  styles: List[StyleConf]
) extends LayerSourceConf {
  private def listParams(expr: Expression): List[String] = {
    def eval(subExpr: Expression): List[String] = subExpr match {
      case v: Variable =>
        List(v.name)
      case _ =>
        subExpr.children.flatMap(eval(_))
    }
    eval(expr)
  }

  def model(possibleSources: List[SimpleWmsSource]): MapAlgebraWmsSource = {
    val layerNames = listParams(algebra)
    val sourceList = layerNames.map { name =>
      val layerSrc = possibleSources.find(_.name == name).getOrElse {
        throw new Exception(
          s"MAML Layer expected but was unable to find the simple layer '$name', make sure all required layers are in the server configuration and are correctly spelled there and in all provided MAML")
      }
      (name -> layerSrc.source)
    }
    MapAlgebraWmsSource(name, title, sourceList.toMap, algebra, styles.map(_.model))
  }

}
