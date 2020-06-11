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

package geotrellis.server.ogc

import geotrellis.server.ogc.wms.wmsScope
import geotrellis.server.ogc.style._
import geotrellis.proj4.CRS
import geotrellis.vector.Extent
import geotrellis.raster.{ResampleMethod, TileLayout, resample}
import geotrellis.raster.render.{ColorMap, ColorRamp}
import com.azavea.maml.ast._
import com.azavea.maml.ast.codec.tree._
import com.typesafe.config._
import geotrellis.raster.io.geotiff.{Auto, AutoHigherResolution, Base, OverviewStrategy}
import io.circe._
import io.circe.parser._
import pureconfig._
import pureconfig.error.CannotConvert
import pureconfig.generic.FieldCoproductHint
import pureconfig.generic.auto._

import scala.util.Try
import scala.collection.JavaConverters._

/** A grab bag of [[ConfigReader]] instances necessary to read the configuration */
package object conf {
  /** Starting 0.11.0 https://github.com/pureconfig/pureconfig/blob/bfc74ce436297b2a9da091e04d362be61108a3cf/CHANGELOG.md#0110-may-9-2019
   * The default transformation in FieldCoproductHint changed
   * from converting class names to lower case to converting them to kebab case.
   */
  implicit def coproductHint[T] = new FieldCoproductHint[T]("type") {
    override def fieldValue(name: String): String = name.toLowerCase
  }

  implicit val circeJsonReader: ConfigReader[Json] =
    ConfigReader[ConfigValue].emap { cv =>
      val renderOptions = ConfigRenderOptions.concise().setJson(true)
      val jsonString = cv.render(renderOptions)
      parse(jsonString) match {
        case Left(parsingFailure) => Left(CannotConvert(jsonString, "json",  parsingFailure.getMessage))
        case Right(json) => Right(json)
      }
    }

  implicit val expressionReader: ConfigReader[Expression] =
    ConfigReader[Json].map { expressionJson =>
      expressionJson.as[Expression] match {
        case Right(success) => success
        case Left(err) => throw err
      }
    }

  implicit val colorRampReader: ConfigReader[ColorRamp] =
    ConfigReader[List[String]].map { colors =>
      ColorRamp(colors.map(java.lang.Long.decode(_).toInt))
    }

  /**
   * HOCON doesn't naturally handle unquoted strings which contain decimals ('.') very well.
   *  As a result, some special configuration handling is required here to allow unquoted
   *  strings specifically when we know we're trying to decode a ColorMap.
   * 
   * @note It is currently difficult to handle double-keyed maps. A workaround
   * has been provided, but it only works with doubles that explicitly decimal
   * pad to tenths (0.0 is OK, 0 is to be avoided)
   */
  implicit val mapDoubleIntReader: ConfigReader[Map[Double, Int]] =
    ConfigReader[Map[String, ConfigValue]].map { cmap =>
      val numericMap = cmap.flatMap({ case (k, v) =>
        v.valueType match {
          case ConfigValueType.OBJECT =>
            val confmap = v.asInstanceOf[ConfigObject].asScala
            confmap.map { case (ck, cv) =>
              val key = k + "." + ck
              val value = cv.unwrapped.asInstanceOf[String]
              key -> value
            }
          case ConfigValueType.STRING => 
            List(k -> v.unwrapped.asInstanceOf[String])
          case _ =>
            List(k -> v.toString)
        }
      }).map({ case (k, v) =>
        val key = k.toDouble
        val value = java.lang.Long.decode(v).toInt
        key -> value 
      }).toMap
      numericMap
    }

  implicit val colormapReader: ConfigReader[ColorMap] =
    ConfigReader[Map[Double, Int]].map { map =>
      ColorMap(map)
    }

  implicit val clipDefinitionReader: ConfigReader[ClipDefinition] =
    ConfigReader[String].emap { str =>
      ClipDefinition.fromString(str) match {
        case Some(cd) =>
          Right(cd)
        case None =>
          Left(CannotConvert(str, "ClipDefinition", s"$str is not a valid ClipDefinition"))
      }
    }

  implicit val keywordConfigReader: ConfigReader[opengis.wms.Keyword] =
    ConfigReader[String].map { str =>
      opengis.wms.Keyword(str)
    }

  implicit val nameConfigReader: ConfigReader[opengis.wms.Name] =
    ConfigReader[String].map { str =>
      opengis.wms.Name.fromString(str, wmsScope)
    }

  implicit val crsReader: ConfigReader[CRS] =
    ConfigReader[Int].map { epsgCode =>
      Try(CRS.fromEpsgCode(epsgCode)).toOption match {
        case Some(crs) => crs
        case None => throw new Exception(s"Invalid EPSG code: ${epsgCode}")
      }
    }

  implicit val extentReader: ConfigReader[Extent] =
    ConfigReader[(Double, Double, Double, Double)].map { case extent @ (xmin, ymin, xmax, ymax) =>
      Try(Extent(xmin, ymin, xmax, ymax)).toOption match {
        case Some(extent) => extent
        case None => throw new Exception(s"Invalid extent: $extent. Should be (xmin, ymin, xmax, ymax)")
      }
    }

  implicit val tileLayoutReader: ConfigReader[TileLayout] =
    ConfigReader[(Int, Int, Int, Int)].map { case layout @ (layoutCols, layoutRows, tileCols, tileRows) =>
      Try(TileLayout(layoutCols, layoutRows, tileCols, tileRows)).toOption match {
        case Some(layout) => layout
        case None => throw new Exception(s"Invalid layout: $layout. Should be (layoutCols, layoutRows, tileCols, tileRows)")
      }
    }

  implicit val resampleMethodReader: ConfigReader[ResampleMethod] =
    ConfigReader[String].map {
      case "nearest-neighbor"  => resample.NearestNeighbor
      case "bilinear"          => resample.Bilinear
      case "cubic-convolution" => resample.CubicConvolution
      case "cubic-spline"      => resample.CubicSpline
      case "lanczos"           => resample.Lanczos
      case "average"           => resample.Average
      case "mode"              => resample.Mode
      case "median"            => resample.Median
      case "max"               => resample.Max
      case "min"               => resample.Min
      case "sum"               => resample.Sum
    }

  implicit val overviewStrategyReader: ConfigReader[OverviewStrategy] = {
    def parse(strategy: String, input: String): OverviewStrategy =
      Auto(Try { input.split(s"$strategy-").last.toInt }.toOption.getOrElse(0))

    def parseAuto(str: String): OverviewStrategy  = parse("auto", str)
    def parseLevel(str: String): OverviewStrategy = parse("level", str)

    ConfigReader[String].map {
      case "auto-higher-resolution"       => AutoHigherResolution
      case "base"                         => Base
      case str if str.startsWith("auto")  => parseAuto(str)
      case str if str.startsWith("level") => parseLevel(str)
      case _                              => OverviewStrategy.DEFAULT
    }
  }
}
