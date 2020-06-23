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

package geotrellis.server.vlm

import geotrellis.proj4.{CRS, WebMercator}
import geotrellis.raster._
import geotrellis.raster.resample._
import geotrellis.raster.io.geotiff._
import geotrellis.layer._

import cats.effect.IO
import cats.data.{NonEmptyList => NEL}
import _root_.io.circe.{Decoder, Encoder}

import java.net.URI

import scala.util.Try

trait RasterSourceUtils {
  implicit val cellTypeEncoder: Encoder[CellType] = Encoder.encodeString.contramap[CellType](CellType.toName)
  implicit val cellTypeDecoder: Decoder[CellType] = Decoder[String].emap { name => Right(CellType.fromName(name)) }

  implicit val uriEncoder: Encoder[URI] = Encoder.encodeString.contramap[URI](_.toString)
  implicit val uriDecoder: Decoder[URI] = Decoder[String].emap { str => Right(URI.create(str)) }

  implicit val resampleMethodEncoder: Encoder[ResampleMethod] =
    Encoder.encodeString.contramap[ResampleMethod] {
      case NearestNeighbor  => "nearest-neighbor"
      case Bilinear         => "bilinear"
      case CubicConvolution => "cubic-convolution"
      case CubicSpline      => "cubic-spline"
      case Lanczos          => "lanczos"
      case Average          => "average"
      case Mode             => "mode"
      case Median           => "median"
      case Max              => "max"
      case Min              => "min"
      case Sum              => "sum"
    }

  implicit val resampleMethodDecoder: Decoder[ResampleMethod] =
    Decoder.decodeString.map {
      case "nearest-neighbor"  => NearestNeighbor
      case "bilinear"          => Bilinear
      case "cubic-convolution" => CubicConvolution
      case "cubic-spline"      => CubicSpline
      case "lanczos"           => Lanczos
      case "average"           => Average
      case "mode"              => Mode
      case "median"            => Median
      case "max"               => Max
      case "min"               => Min
      case "sum"               => Sum
    }

  implicit val overviewStrategyEncoder: Encoder[OverviewStrategy] =
    Encoder.encodeString.contramap[OverviewStrategy] {
      case AutoHigherResolution => "auto-higher-resolution"
      case Base                 => "base"
      case Auto(n)              => s"auto-$n"
      case Level(n)             => s"level-$n"
    }

  implicit val overviewStrategyDecoder: Decoder[OverviewStrategy] = {
    def parse(strategy: String, input: String): OverviewStrategy =
      Auto(Try { input.split(s"$strategy-").last.toInt }.toOption.getOrElse(0))

    def parseAuto(str: String): OverviewStrategy  = parse("auto", str)
    def parseLevel(str: String): OverviewStrategy = parse("level", str)

    Decoder.decodeString.map {
      case "auto-higher-resolution"       => AutoHigherResolution
      case "base"                         => Base
      case str if str.startsWith("auto")  => parseAuto(str)
      case str if str.startsWith("level") => parseLevel(str)
      case _                              => OverviewStrategy.DEFAULT
    }
  }

  def getRasterSource(uri: String): RasterSource

  // the target CRS
  val crs: CRS = WebMercator

  val tmsLevels: Array[LayoutDefinition] = {
    val scheme = ZoomedLayoutScheme(crs, 256)
    for (zoom <- 0 to 64) yield scheme.levelForZoom(zoom).layout
  }.toArray

  def fetchTile(
    uri: String,
    zoom: Int,
    x: Int,
    y: Int,
    crs: CRS = WebMercator,
    method: ResampleMethod = ResampleMethod.DEFAULT,
    target: ResampleTarget = DefaultTarget,
    overviewStrategy: OverviewStrategy = OverviewStrategy.DEFAULT
  ): IO[Raster[MultibandTile]] =
    IO {
      val key = SpatialKey(x, y)
      val ld = tmsLevels(zoom)
      val rs = getRasterSource(uri)
        .reproject(crs, target)
        .tileToLayout(ld, identity, method, overviewStrategy)

      rs.read(key).map(Raster(_, ld.mapTransform(key)))
    } flatMap {
        case Some(t) =>
          IO.pure(t)
        case _ =>
          IO.raiseError(new Exception(s"No Tile availble for the following SpatialKey: ${x}, ${y}"))
    }

  def getCRS(uri: String): IO[CRS] = IO { getRasterSource(uri).crs }
  def getRasterExtents(uri: String): IO[NEL[RasterExtent]] = IO {
    val rs = getRasterSource(uri)
    NEL.fromList(rs.resolutions.map { cs =>
      RasterExtent(rs.extent, cs)
    }).getOrElse(NEL(rs.gridExtent.toRasterExtent, Nil))
  }
}
