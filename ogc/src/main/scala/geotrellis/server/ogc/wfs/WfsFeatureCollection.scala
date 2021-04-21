/*
 * Copyright 2021 Azavea
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

package geotrellis.server.ogc.wfs

import io.circe.syntax._
import io.circe.Json
import cats.syntax.option._
import geotrellis.proj4.CRS
import geotrellis.raster.CellSize
import geotrellis.server.ogc.URN
import geotrellis.server.ogc.gml.GmlDataRecord
import geotrellis.vector._
import opengis._
import opengis.wfs._
import opengis.gml.{
  CoordinatesType,
  GeometryPropertyType,
  GeometryPropertyTypeSequence1,
  LinearRingType,
  MultiPolygonType,
  PolygonPropertyType,
  PolygonPropertyTypeSequence1,
  PolygonType,
  StandardObjectPropertiesSequence
}
import scalaxb.DataRecord

import java.net.URI
import scala.xml.{Elem, NodeSeq}

object WfsFeatureCollection {
  def toXML[G <: Geometry](features: Seq[Feature[G, Json]], crs: CRS, cellSize: CellSize): Elem = {
    scalaxb
      .toXML[FeatureCollectionType](
        obj = apply(features, crs, cellSize),
        namespace = None,
        elementLabel = "FeatureCollection".some,
        scope = wfsScope,
        typeAttribute = false
      )
      .asInstanceOf[Elem]
  }

  private def jsonXML(j: Json): NodeSeq =
    j.asObject match {
      case Some(obj) =>
        obj.toMap
          .map { case (k, v) =>
            v.asObject match {
              case Some(obj) => jsonXML(obj.asJson)
              case _         =>
                scala.xml.XML
                  .loadString(s"<$k>${v.noSpaces}</$k>")
            }
          }
          .foldLeft(NodeSeq.Empty)(_ ++ _)
      case _         => DataRecord(j.noSpaces).nested
    }

  private def propertiesXML[G <: Geometry](f: Feature[G, Json]): NodeSeq = jsonXML(f.data)

  def apply[G <: Geometry](features: Seq[Feature[G, Json]], crs: CRS, cellSize: CellSize): FeatureCollectionType = {
    FeatureCollectionType(
      standardObjectPropertiesSequence1 = StandardObjectPropertiesSequence(),
      featureMember = features.map { feature =>
        val polygons = feature.geom match {
          case p: Polygon       => p :: Nil
          case mp: MultiPolygon => mp.polygons.toList
          case po: Point        =>
            val (x, y)         = po.getX -> po.getY
            val CellSize(w, h) = cellSize
            Polygon(
              (x - w, y - h),
              (x + w, y - h),
              (x + w, y + h),
              (x - w, y + h),
              (x - w, y - h)
            ) :: Nil
          case _                => Nil
        }

        opengis.gml.FeaturePropertyType(
          Some(
            opengis.gml.FeaturePropertyTypeSequence1(
              DataRecord(
                None,
                "PixelPerBandFeature".some,
                DataRecord(
                  None,
                  "Geometry".some,
                  GeometryPropertyType(
                    GeometryPropertyTypeSequence1(
                      GmlDataRecord(
                        MultiPolygonType(
                          standardObjectPropertiesSequence1 = StandardObjectPropertiesSequence(),
                          polygonMember = polygons.map { polygon =>
                            PolygonPropertyType(
                              PolygonPropertyTypeSequence1(
                                PolygonType(
                                  StandardObjectPropertiesSequence(),
                                  exterior = DataRecord(
                                    "gml".some,
                                    "gml:exterior".some,
                                    GmlDataRecord(
                                      LinearRingType(
                                        StandardObjectPropertiesSequence(),
                                        linearringtypeoption = GmlDataRecord(
                                          CoordinatesType(
                                            polygon.getExteriorRing.getCoordinates
                                              .map { c => s"${c.getX},${c.getY}" }
                                              .toList
                                              .mkString(" "),
                                            attributes = Map(
                                              "@decimal" -> DataRecord("."),
                                              "@cs"      -> DataRecord(","),
                                              "@ts"      -> DataRecord(" ")
                                            )
                                          )
                                        ) :: Nil
                                      )
                                    ).nested
                                      .nestedXML("gml:exterior")
                                  ).some
                                )
                              ).some
                            )
                          },
                          attributes = Map("@srsName" -> DataRecord(URN.fromCrs(crs).map(new URI(_)).get))
                        )
                      )
                    ).some
                  )
                ).nested ++ propertiesXML(feature)
              ) // .nestedSeq.nestedXML("Pixel_Per_Band_Feature")
            )
          )
        )
      }
    )
  }
}
