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

package geotrellis.store.query

import geotrellis.proj4.LatLng
import geotrellis.vector.{Extent, ProjectedExtent}
import cats.syntax.either._
import cats.syntax.option._
import _root_.io.circe.parser._
import _root_.io.circe.syntax._
import geotrellis.store.query.vector.ProjectedGeometry

import java.time.{ZoneOffset, ZonedDateTime}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

class QueryFSpec extends AnyFunSpec with Matchers {
  describe("QueryF evaluation specs") {
    def dtFromMonth(month: Int): ZonedDateTime = ZonedDateTime.of(2020, month, 1, 0, 0, 1, 0, ZoneOffset.UTC)

    val extent  = ProjectedGeometry(Extent(0, 0, 2, 2), LatLng)
    val extent2 = ProjectedGeometry(Extent(1, 1, 4, 4), LatLng)
    val dt      = dtFromMonth(1)

    it("should convert AST into json") {
      val query  = (intersects(extent) and intersects(extent2)) and at(dt)
      val actual = query.asJson

      val expected =
        parse("""
                |{
                |  "And": {
                |    "left": {
                |      "And": {
                |        "left": {
                |          "Intersects": {
                |            "projectedGeometry": {
                |              "geometry": {
                |                "type": "Polygon",
                |                "coordinates": [
                |                  [
                |                    [
                |                      0,
                |                      0
                |                    ],
                |                    [
                |                      0,
                |                      2
                |                    ],
                |                    [
                |                      2,
                |                      2
                |                    ],
                |                    [
                |                      2,
                |                      0
                |                    ],
                |                    [
                |                      0,
                |                      0
                |                    ]
                |                  ]
                |                ]
                |              },
                |              "crs": "+proj=longlat +datum=WGS84 +no_defs "
                |            }
                |          }
                |        },
                |        "right": {
                |          "Intersects": {
                |            "projectedGeometry": {
                |              "geometry": {
                |                "type": "Polygon",
                |                "coordinates": [
                |                  [
                |                    [
                |                      1,
                |                      1
                |                    ],
                |                    [
                |                      1,
                |                      4
                |                    ],
                |                    [
                |                      4,
                |                      4
                |                    ],
                |                    [
                |                      4,
                |                      1
                |                    ],
                |                    [
                |                      1,
                |                      1
                |                    ]
                |                  ]
                |                ]
                |              },
                |              "crs": "+proj=longlat +datum=WGS84 +no_defs "
                |            }
                |          }
                |        }
                |      }
                |    },
                |    "right": {
                |      "At": {
                |        "time": "2020-01-01T00:00:01Z",
                |        "fieldName": "time"
                |      }
                |    }
                |  }
                |}
                |""".stripMargin).valueOr(throw _)

      actual shouldBe expected
    }

    it("should build AST from json") {
      val json =
        parse("""
                |{
                |  "And" : {
                |    "left" : {
                |      "And" : {
                |        "left" : {
                |          "Intersects" : {
                |            "projectedGeometry" : {
                |              "geometry" : {
                |                "type" : "Polygon",
                |                "coordinates" : [
                |                  [
                |                    [
                |                      0.0,
                |                      0.0
                |                    ],
                |                    [
                |                      0.0,
                |                      2.0
                |                    ],
                |                    [
                |                      2.0,
                |                      2.0
                |                    ],
                |                    [
                |                      2.0,
                |                      0.0
                |                    ],
                |                    [
                |                      0.0,
                |                      0.0
                |                    ]
                |                  ]
                |                ]
                |              },
                |              "crs" : "+proj=longlat +datum=WGS84 +no_defs "
                |            }
                |          }
                |        },
                |        "right" : {
                |          "Intersects" : {
                |            "projectedGeometry" : {
                |              "geometry" : {
                |                "type" : "Polygon",
                |                "coordinates" : [
                |                  [
                |                    [
                |                      1.0,
                |                      1.0
                |                    ],
                |                    [
                |                      1.0,
                |                      4.0
                |                    ],
                |                    [
                |                      4.0,
                |                      4.0
                |                    ],
                |                    [
                |                      4.0,
                |                      1.0
                |                    ],
                |                    [
                |                      1.0,
                |                      1.0
                |                    ]
                |                  ]
                |                ]
                |              },
                |              "crs" : "+proj=longlat +datum=WGS84 +no_defs "
                |            }
                |          }
                |        }
                |      }
                |    },
                |    "right" : {
                |      "At" : {
                |        "time" : "2020-01-01T00:00:01Z",
                |        "fieldName" : "time"
                |      }
                |    }
                |  }
                |}
                |""".stripMargin).valueOr(throw _)

      val expected = (intersects(extent) and intersects(extent2)) and at(dt)
      val actual   = json.as[Query].valueOr(throw _)

      actual shouldBe expected
    }

    it("should filter raster sources catalog both using JSON and direct AST query representations") {
      def dtFromMonth(month: Int): ZonedDateTime = ZonedDateTime.of(2020, month, 1, 0, 0, 1, 0, ZoneOffset.UTC)
      val dt1                                    = dtFromMonth(1)
      val dt2                                    = dtFromMonth(2)
      val dt3                                    = dtFromMonth(3)
      val ex1                                    = ProjectedExtent(Extent(0, 0, 2, 2), LatLng)
      val ex2                                    = ProjectedExtent(Extent(1, 1, 4, 4), LatLng)
      val ex3                                    = ProjectedExtent(Extent(2, 2, 5, 5), LatLng)
      val ex4                                    = ProjectedExtent(Extent(6, 6, 10, 10), LatLng)

      val store =
        EmptyRasterSource("first", ex1, dt1.some) ::
          EmptyRasterSource("second", ex2, dt2.some) ::
          EmptyRasterSource("third", ex3, dt2.some) ::
          EmptyRasterSource("fourth", ex4, dt3.some) :: Nil

      val query = (intersects(ProjectedGeometry(ex2)) and intersects(ProjectedGeometry(ex3))) and at(dt2)
      // scheme.cata(RasterSourceRepository.algebra[EmptyRasterSource]).apply(query)(store)
      val result = RasterSourceRepository.eval(query)(store)

      result shouldBe EmptyRasterSource("second", ex2, dt2.some) :: EmptyRasterSource("third", ex3, dt2.some) :: Nil

      val repository = RasterSourceRepository(store)
      val rresult    = repository.find(query)

      rresult shouldBe result

      val jsonQuery = query.asJson
      // scheme.hylo(RasterSourceRepository.algebra[EmptyRasterSource], QueryF.coalgebraJson).apply(jsonQuery)(store)
      val hresult = RasterSourceRepository.eval(jsonQuery)(store)

      hresult shouldBe result
    }
  }
}
