package geotrellis.server.ogc.conf

import geotrellis.server.ogc.wms.source._

import geotrellis.contrib.vlm.RasterSource
import com.azavea.maml.ast._
import cats._
import cats.implicits._

import java.net.{InetAddress, URL}

sealed trait SourceConf {
  def name: String
  def styles: List[StyleConf]
}

case class SimpleSourceConf(
  name: String,
  title: String,
  source: RasterSourceConf,
  styles: List[StyleConf]
) extends SourceConf {
  def model: SimpleWmsSource =
    SimpleWmsSource(name, title, source.toRasterSource, styles.map(_.model))
}

case class MapAlgebraSourceConf(
  name: String,
  title: String,
  algebra: Expression,
  styles: List[StyleConf]
) extends SourceConf {
  def model(possibleSources: List[SimpleWmsSource]): MapAlgebraWmsSource = {
    val layerNames = Hack.listParams(algebra)
    layerNames.traverse { name =>
      val maybeSource = possibleSources.find(_.name == name)
      maybeSource.map { name -> _.source }
    }.map { srcList =>
      MapAlgebraWmsSource(name, title, srcList.toMap, algebra, styles.map(_.model))
    }.getOrElse {
      throw new Exception("Bad mapalgebra wms configuration: unable to find binding parameters")
    }
  }
}

object Hack {
  def listParams(expr: Expression): List[String] = {
    def eval(subExpr: Expression): List[String] = subExpr match {
      case v: Variable =>
        List(v.name)
      case _ =>
        subExpr.children.flatMap(eval(_))
    }
    eval(expr)
  }
}
