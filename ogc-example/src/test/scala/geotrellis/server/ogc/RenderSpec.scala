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

import geotrellis.server.extent.SampleUtils

import geotrellis.raster._
import geotrellis.vector._
import com.azavea.maml.error._
import cats._
import cats.effect._
import cats.data.{NonEmptyList => NEL}
import cats.implicits._

import org.scalatest._

import scala.util.Random
import scala.concurrent.ExecutionContext


class RenderSpec extends FunSpec with Matchers {
  implicit val cs = cats.effect.IO.contextShift(ExecutionContext.global)

  describe("Linear Interpolation") {
    it("should not throw when duplicate breaks are encountered") {
      Render.linearInterpolationBreaks(Array(0, 0, 80), 255)
    }
  }
}
