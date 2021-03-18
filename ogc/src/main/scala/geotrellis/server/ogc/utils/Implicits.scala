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

package geotrellis.server.ogc.utils

import geotrellis.server.ogc._
import com.azavea.maml.ast.Expression
import geotrellis.raster.{MosaicRasterSource, RasterSource}

import cats.data.{NonEmptyList => NEL}
import cats.syntax.option._
import scalaxb.DataRecord

import java.time.ZonedDateTime
import scala.xml.Elem

trait Implicits {
  implicit class DataRecordMethods[A](dataRecord: DataRecord[A]) {
    def toXML: Elem = ScalaxbUtils.toXML(dataRecord)
  }

  implicit class ExpressionMethods(expr: Expression) {
    def bindExtendedParameters(fun: Expression => Expression): Expression =
      bindExtendedParameters(fun.some)

    def bindExtendedParameters(fun: Option[Expression => Expression]): Expression =
      fun.fold(expr)(ExpressionUtils.bindExpression(expr, _))
  }

  implicit class OgcRasterSourceOps(val source: RasterSource) {
    def time(timeMetadataKey: Option[String]): OgcTime =
      timeMetadataKey
        .flatMap { key =>
          source match {
            case mrs: MosaicRasterSource =>
              mrs.attributes.times(timeMetadataKey).orElse {
                val times = mrs.metadata.list.toList.flatMap(_.attributes.get(key)).map(ZonedDateTime.parse)
                times match {
                  case head :: tail => OgcTimePositions(NEL(head, tail)).some
                  case _ => None
                }
              }

            case _                       => source.attributes.get(key).map(ZonedDateTime.parse).map(OgcTimePositions(_))
          }
        }
        .getOrElse(OgcTimeEmpty)
  }

  implicit class AttributesOps(val self: Map[String, String]) {
    def time(timeMetadataKey: Option[String]): Option[OgcTime] =
      timeMetadataKey
        .flatMap(self.get(_).map(OgcTime.fromString))

    def times(timeMetadataKey: Option[String]): Option[OgcTime] =
      timeMetadataKey.flatMap { key =>
        val times = self.filterKeys(_.endsWith(key)).values.toList.map(ZonedDateTime.parse)

        times match {
          case head :: tail => OgcTimePositions(NEL(head, tail)).some
          case _            => None
        }
      }
  }
}

object Implicits extends Implicits
