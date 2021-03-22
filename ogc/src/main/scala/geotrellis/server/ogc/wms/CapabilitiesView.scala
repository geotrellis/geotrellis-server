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

package geotrellis.server.ogc.wms

import geotrellis.server.ogc._
import geotrellis.server.ogc.style._
import geotrellis.proj4.{CRS, LatLng}
import geotrellis.raster.{CellSize, GridExtent}
import geotrellis.raster.reproject.Reproject.Options
import geotrellis.raster.reproject.ReprojectRasterExtent
import geotrellis.vector.Extent
import cats.Apply
import cats.syntax.apply._
import cats.syntax.option._
import opengis.wms._
import opengis._
import scalaxb._
import cats.Functor
import cats.syntax.functor._

import java.net.URL
import scala.xml.Elem
import cats.Monad

/** @param model Model of layers we can report
  * @param serviceUrl URL where this service can be reached with addition of `?request=` query parameter
  */
class CapabilitiesView[F[_]: Functor: Apply: Monad](model: WmsModel[F], serviceUrl: URL, extendedCapabilities: List[DataRecord[Elem]] = Nil) {
  import CapabilitiesView._

  def toXML: F[Elem] = {
    val getCapabilities = OperationType(
      Format = "text/xml" :: Nil,
      DCPType = DCPType(
        HTTP(
          Get = Get(
            OnlineResource(
              Map(
                "@{http://www.w3.org/1999/xlink}href" -> DataRecord(
                  serviceUrl.toURI
                ),
                "@{http://www.w3.org/1999/xlink}type" -> DataRecord(
                  xlink.Simple: xlink.TypeType
                )
              )
            )
          )
        )
      ) :: Nil
    )

    val getMap = OperationType(
      Format = "image/png" :: "image/jpeg" :: Nil,
      DCPType = DCPType(
        HTTP(
          Get = Get(
            OnlineResource(
              Map(
                "@{http://www.w3.org/1999/xlink}href" -> DataRecord(
                  serviceUrl.toURI
                ),
                "@{http://www.w3.org/1999/xlink}type" -> DataRecord(
                  xlink.Simple: xlink.TypeType
                )
              )
            )
          )
        )
      ) :: Nil
    )

    modelAsLayer(model.parentLayerMeta, model) map { layer =>
      val capability = Capability(
        Request = Request(
          GetCapabilities = getCapabilities,
          GetMap = getMap,
          GetFeatureInfo = None
        ),
        Exception = Exception("XML" :: "INIMAGE" :: "BLANK" :: Nil),
        Layer = layer.some,
        _ExtendedCapabilities = extendedCapabilities
      )

      scalaxb
        .toXML[opengis.wms.WMS_Capabilities](
          obj = WMS_Capabilities(
            model.serviceMeta,
            capability,
            Map("@version" -> DataRecord("1.3.0"))
          ),
          namespace = None,
          elementLabel = "WMS_Capabilities".some,
          scope = wmsScope,
          typeAttribute = false
        )
        .asInstanceOf[scala.xml.Elem]
    }
  }
}

object CapabilitiesView {
  implicit def toRecord[T: CanWriteXML](t: T): DataRecord[T] = DataRecord(t)

  def boundingBox(crs: CRS, extent: Extent, cellSize: CellSize): BoundingBox =
    if (crs.isGeographic)
      BoundingBox(
        Map(
          "@CRS"  -> s"EPSG:${crs.epsgCode.get}",
          "@minx" -> extent.ymin,
          "@miny" -> extent.xmin,
          "@maxx" -> extent.ymax,
          "@maxy" -> extent.xmax,
          "@resx" -> cellSize.width,
          "@resy" -> cellSize.height
        )
      )
    else
      BoundingBox(
        Map(
          "@CRS"  -> s"EPSG:${crs.epsgCode.get}",
          "@minx" -> extent.xmin,
          "@miny" -> extent.ymin,
          "@maxx" -> extent.xmax,
          "@maxy" -> extent.ymax,
          "@resx" -> cellSize.width,
          "@resy" -> cellSize.height
        )
      )

  implicit class StyleMethods(val style: OgcStyle) {
    def render: Style =
      Style(
        Name = style.name,
        Title = style.title,
        LegendURL = style.legends.map(_.toLegendURL)
      )
  }

  implicit class RasterSourceMethods(val source: OgcSource) {
    def toLayer(parentProjections: List[CRS]): Layer = {
      Layer(
        Name = source.name.some,
        Title = source.title,
        Abstract = None,
        KeywordList = None,
        // extra CRS that is supported by this layer
        CRS = (parentProjections ++ source.nativeCrs).distinct.map { crs =>
          crs.epsgCode
            .map { code => s"EPSG:$code" }
            .getOrElse(throw new java.lang.Exception(s"Unable to construct EPSG code from $crs"))
        },
        EX_GeographicBoundingBox = {
          val llre = source match {
            case MapAlgebraSource(_, _, rss, _, _, _, resampleMethod, _, _) =>
              rss.values
                .map { rs =>
                  ReprojectRasterExtent(
                    rs.gridExtent,
                    rs.crs,
                    LatLng,
                    Options.DEFAULT.copy(resampleMethod)
                  )
                }
                .reduce { (re1, re2) =>
                  val e  = re1.extent combine re2.extent
                  val cs =
                    if (re1.cellSize.resolution < re2.cellSize.resolution)
                      re1.cellSize
                    else re2.cellSize
                  new GridExtent[Long](e, cs)
                }
            case rasterOgcLayer: RasterOgcSource                            =>
              val rs = rasterOgcLayer.source
              ReprojectRasterExtent(
                rs.gridExtent,
                rs.crs,
                LatLng,
                Options.DEFAULT.copy(rasterOgcLayer.resampleMethod)
              )
          }

          /** TODO: replace with source.extentIn(LatLng)
            * see: https://github.com/locationtech/geotrellis/issues/3258
            */
          val Extent(xmin, ymin, xmax, ymax) = llre.extent
          EX_GeographicBoundingBox(xmin, xmax, ymin, ymax).some
        },
        BoundingBox = Nil,
        Dimension = source.time match {
          case tp @ OgcTimePositions(nel)        =>
            Dimension(
              tp.toString,
              Map(
                "@name"    -> DataRecord("time"),
                "@units"   -> DataRecord("ISO8601"),
                "@default" -> DataRecord(nel.head.toInstant.toString)
              )
            ) :: Nil
          case ti @ OgcTimeInterval(start, _, _) =>
            Dimension(
              ti.toString,
              Map(
                "@name"    -> DataRecord("time"),
                "@units"   -> DataRecord("ISO8601"),
                "@default" -> DataRecord(start.toString)
              )
            ) :: Nil
          case OgcTimeEmpty                      => Nil
        },
        Attribution = None,
        AuthorityURL = Nil,
        Identifier = Nil,
        MetadataURL = Nil,
        DataURL = Nil,
        FeatureListURL = Nil,
        Style = source.styles.map(_.render),
        MinScaleDenominator = None,
        MaxScaleDenominator = None,
        Layer = Nil,
        attributes = Map.empty
      )
    }
  }

  def modelAsLayer[F[_]: Monad](parentLayerMeta: WmsParentLayerMeta, model: WmsModel[F]): F[Layer] = {
    val bboxAndLayers = model.sources.store map { sources =>
      val bboxes  = sources map { source =>
        val llre = source match {
          case MapAlgebraSource(_, _, rss, _, _, _, resampleMethod, _, _) =>
            rss.values
              .map { rs => ReprojectRasterExtent(rs.gridExtent, rs.crs, LatLng, Options.DEFAULT.copy(resampleMethod)) }
              .reduce { (re1, re2) =>
                val e  = re1.extent combine re2.extent
                val cs = if (re1.cellSize.resolution < re2.cellSize.resolution) re1.cellSize else re2.cellSize
                new GridExtent[Long](e, cs)
              }
          case rasterOgcLayer: RasterOgcSource                            =>
            val rs = rasterOgcLayer.source
            ReprojectRasterExtent(rs.gridExtent, rs.crs, LatLng, Options.DEFAULT.copy(rasterOgcLayer.resampleMethod))
        }

        /** TODO: replace with
          * val llExtents = model.sources.store.map(_.extentIn(LatLng))
          * val llExtent = llExtents.tail.fold(llExtents.head)(_ combine _)
          * see: https://github.com/locationtech/geotrellis/issues/3258
          */
        llre.extent

      }
      val bbox    = bboxes.tail.fold(bboxes.head)(_ combine _)
      val ogcBbox = EX_GeographicBoundingBox(bbox.xmin, bbox.xmax, bbox.ymin, bbox.ymax).some
      (ogcBbox, sources.map(_.toLayer(parentLayerMeta.supportedProjections)))
    }
    (bboxAndLayers, model.time).mapN { case ((bbox, layers), time) =>
      Layer(
        Name = parentLayerMeta.name,
        Title = parentLayerMeta.title,
        Abstract = parentLayerMeta.description,
        KeywordList = None,
        // All layers are avail at least at this CRS
        // All sublayers would have metadata in this CRS + its own
        CRS = parentLayerMeta.supportedProjections.distinct.map { crs =>
          crs.epsgCode
            .map { code =>
              s"EPSG:$code"
            }
            .getOrElse(
              throw new java.lang.Exception(
                s"Unable to construct EPSG code from $crs"
              )
            )
        },
        // Extent of all layers in default CRS
        // Should it be world extent? To simplify tests and QGIS work it's all RasterSources extent
        EX_GeographicBoundingBox = bbox,
        // TODO: bounding box for global layer
        BoundingBox = Nil,
        Dimension = time match {
          case tp @ OgcTimePositions(nel)        =>
            Dimension(
              tp.toString,
              Map(
                "@name"    -> DataRecord("time"),
                "@units"   -> DataRecord("ISO8601"),
                "@default" -> DataRecord(nel.head.toInstant.toString)
              )
            ) :: Nil
          case ti @ OgcTimeInterval(start, _, _) =>
            Dimension(
              ti.toString,
              Map(
                "@name"    -> DataRecord("time"),
                "@units"   -> DataRecord("ISO8601"),
                "@default" -> DataRecord(start.toString)
              )
            ) :: Nil
          case OgcTimeEmpty                      => Nil
        },
        Attribution = None,
        AuthorityURL = Nil,
        Identifier = Nil,
        MetadataURL = Nil,
        DataURL = Nil,
        FeatureListURL = Nil,
        Style = Nil,
        MinScaleDenominator = None,
        MaxScaleDenominator = None,
        Layer = layers,
        attributes = Map.empty
      )
    }
  }
}
