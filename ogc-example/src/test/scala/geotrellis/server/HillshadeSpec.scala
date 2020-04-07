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

package geotrellis.server

import com.azavea.maml.ast.{FocalHillshade, RasterLit}
import com.azavea.maml.error.Interpreted
import com.azavea.maml.eval.{Interpreter, Result}

import geotrellis.proj4.LatLng
import geotrellis.raster.{MultibandTile, ProjectedRaster, Raster, RasterExtent}
import geotrellis.raster.io.geotiff.{AutoHigherResolution, GeoTiff}
import geotrellis.store._
import geotrellis.raster.resample._
import geotrellis.vector.Extent
import cats.data.Validated.{Invalid, Valid}

import scala.reflect.ClassTag

import org.scalatest.FunSpec
import org.scalatest._

class HillshadeSpec extends FunSpec with Matchers {
  implicit class TypeRefinement(self: Interpreted[Result]) {
    def as[T: ClassTag]: Interpreted[T] = self match {
      case Valid(r) => r.as[T]
      case i @ Invalid(_) => i
    }
  }

  // https://github.com/geotrellis/geotrellis-server/issues/150
  describe("HillshadeSpec") {
    ignore("RasterSource reproject hillshade") {
      val uri = "gt+s3://azavea-datahub/catalog?layer=us-ned-tms-epsg3857&zoom=14&band_count=1"
      val rs = new GeoTrellisRasterSource(uri)
      val raster =
        rs
          .reprojectToRegion(
            LatLng,
            RasterExtent(
              Extent(-120.2952713630537, 39.13161870369179, -120.1235160949708, 39.25813307365495),
              2.0018096513158E-4,2.0018096513159E-4,
              858, 632
            ),
            Bilinear,
            AutoHigherResolution
          )
          .read(Extent(-120.2952713630537, 39.13161870369179, -120.1235160949708, 39.25813307365495))
          .get

      val hillshadeProjectedRaster = ProjectedRaster(raster, LatLng)

      val interpreter = Interpreter.DEFAULT
      val res = interpreter(FocalHillshade(List(RasterLit(hillshadeProjectedRaster)), 315, 45)).as[MultibandTile]

      res match {
        case Valid(t) => GeoTiff(Raster(t, raster.extent), LatLng).write("/tmp/rs-reproject-hillshade.tiff")
        case i@Invalid(_) => fail(s"$i")
      }
    }
  }
}
