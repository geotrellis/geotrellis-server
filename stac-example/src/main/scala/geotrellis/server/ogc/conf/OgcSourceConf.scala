/*
 * Copyright 2020 Azavea
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package geotrellis.server.ogc.conf

import geotrellis.server.ogc.stac.{ByCollection, ByLayer, StacSearchCriteria}

import geotrellis.raster.RasterSource
import geotrellis.raster.io.geotiff.OverviewStrategy
import geotrellis.raster.resample._
import geotrellis.server.ogc._
import geotrellis.store.GeoTrellisPath
import geotrellis.proj4.{CRS, WebMercator}
import com.azavea.maml.ast._
import cats.syntax.option._
import eu.timepit.refined.types.numeric.NonNegInt
import geotrellis.stac.raster.StacCollectionSource
import pureconfig.ConfigReader

import scala.util.Try

// This sumtype corresponds to the in-config representation of a source
sealed trait OgcSourceConf {
  def name: String
  def styles: List[StyleConf]
  def resampleMethod: ResampleMethod
  def overviewStrategy: OverviewStrategy
}

case class StacSourceConf(
  name: String,
  layer: Option[String],
  collection: Option[String],
  title: String,
  source: String,
  asset: String,
  assetLimit: Option[NonNegInt],
  defaultStyle: Option[String],
  styles: List[StyleConf],
  commonCrs: CRS = WebMercator,
  resampleMethod: ResampleMethod = ResampleMethod.DEFAULT,
  overviewStrategy: OverviewStrategy = OverviewStrategy.DEFAULT,
  ignoreTime: Boolean = false,
  datetimeField: String = "datetime",
  timeFormat: OgcTimeFormat = OgcTimeFormat.Self,
  timeDefault: OgcTimeDefault = OgcTimeDefault.Oldest,
  computeTimePositions: Boolean = false,
  withGDAL: Boolean = false,
  parallelMosaic: Boolean = false
) extends OgcSourceConf {

  /** By default the search would happen across collections. */
  def searchCriteria: StacSearchCriteria =
    (collection, layer) match {
      case (None, Some(_)) => ByLayer
      case _               => ByCollection
    }

  def searchName: String =
    (searchCriteria match {
      case ByCollection => collection
      case ByLayer      => layer
    }).getOrElse("")

  def toLayer(rs: RasterSource): SimpleSource =
    SimpleSource(name, title, rs, defaultStyle, styles.map(_.toStyle), resampleMethod, overviewStrategy, datetimeField.some, timeFormat)

  def toLayer(rs: StacCollectionSource): StacOgcSource =
    StacOgcSource(
      name,
      title,
      rs,
      defaultStyle,
      styles.map(_.toStyle),
      resampleMethod,
      overviewStrategy,
      datetimeField.some,
      computeTimePositions,
      timeFormat,
      timeDefault
    )
}

object StacSourceConf {
  implicit val crsReader: ConfigReader[CRS] =
    ConfigReader[String].map { str =>
      Try(CRS.fromName(str)).toOption orElse Try(CRS.fromString(str)).toOption match {
        case Some(crs) => crs
        case None      => throw new Exception(s"Invalid Proj4 String: $str")
      }
    }
}

case class RasterSourceConf(
  name: String,
  title: String,
  source: String,
  defaultStyle: Option[String],
  styles: List[StyleConf],
  resampleMethod: ResampleMethod = ResampleMethod.DEFAULT,
  overviewStrategy: OverviewStrategy = OverviewStrategy.DEFAULT,
  datetimeField: String = SimpleSource.TimeFieldDefault,
  timeFormat: OgcTimeFormat = OgcTimeFormat.Self,
  timeDefault: OgcTimeDefault = OgcTimeDefault.Oldest
) extends OgcSourceConf {
  def toLayer: RasterOgcSource =
    GeoTrellisPath
      .parseOption(source)
      .fold[RasterOgcSource](
        SimpleSource(
          name,
          title,
          RasterSource(source),
          defaultStyle,
          styles.map(_.toStyle),
          resampleMethod,
          overviewStrategy,
          datetimeField.some,
          timeFormat
        )
      )(_ =>
        GeoTrellisOgcSource(
          name,
          title,
          source,
          defaultStyle,
          styles.map(_.toStyle),
          resampleMethod,
          overviewStrategy,
          datetimeField.some,
          timeFormat,
          timeDefault
        )
      )
}

case class MapAlgebraSourceConf(
  name: String,
  title: String,
  algebra: Expression,
  defaultStyle: Option[String],
  styles: List[StyleConf],
  resampleMethod: ResampleMethod = ResampleMethod.DEFAULT,
  overviewStrategy: OverviewStrategy = OverviewStrategy.DEFAULT,
  timeFormat: OgcTimeFormat = OgcTimeFormat.Self,
  timeDefault: OgcTimeDefault = OgcTimeDefault.Oldest
) extends OgcSourceConf {
  def listParams(expr: Expression): List[String] = {
    def eval(subExpr: Expression): List[String] =
      subExpr match {
        case v: Variable => List(v.name)
        case _           => subExpr.children.flatMap(eval)
      }
    eval(expr)
  }

  /** Given a list of all available `SimpleSourceConf` instances in the global [[Conf]] object,
    *  attempt to produce the parameter bindings necessary for evaluating the MAML [[Expression]]
    *  in the algebra field
    */
  def model(possibleSources: List[RasterOgcSource]): MapAlgebraSource = {
    val layerNames = listParams(algebra)
    val sourceList = layerNames.map { name =>
      val layerSrc = possibleSources.find(_.name == name).getOrElse {
        throw new Exception(
          s"MAML Layer expected but was unable to find the simple layer '$name', make sure all required layers are in the server configuration and are correctly spelled there and in all provided MAML"
        )
      }
      name -> layerSrc
    }
    MapAlgebraSource(
      name,
      title,
      sourceList.toMap,
      algebra,
      defaultStyle,
      styles.map(_.toStyle),
      resampleMethod,
      overviewStrategy,
      timeFormat,
      timeDefault
    )
  }

  def modelOpt(possibleSources: List[RasterOgcSource]): Option[MapAlgebraSource] = {
    val layerNames = listParams(algebra)
    val sourceList = layerNames.flatMap { name => possibleSources.find(_.name == name).map { layerSrc => name -> layerSrc } }
    if (sourceList.length == layerNames.length)
      MapAlgebraSource(
        name,
        title,
        sourceList.toMap,
        algebra,
        defaultStyle,
        styles.map(_.toStyle),
        resampleMethod,
        overviewStrategy,
        timeFormat,
        timeDefault
      ).some
    else None
  }
}
