package geotrellis.server.wcs

import geotrellis.server.wcs.params._
import geotrellis.server.wcs.ops._
import geotrellis.spark._
import geotrellis.spark.io._

import akka.http.scaladsl.marshallers.xml.ScalaXmlSupport._
import akka.http.scaladsl.server._
import akka.http.scaladsl.server.Directives._
import cats.data.Validated
import Validated._
import com.typesafe.scalalogging.LazyLogging
import com.typesafe.config.ConfigFactory
import geotrellis.spark.io.AttributeStore

import scala.util.Try
import scala.xml.NodeSeq

object WcsRoute extends LazyLogging {
  type MetadataCatalog = Map[String, (Seq[Int], Option[TileLayerMetadata[SpatialKey]])]

  val catalogMetadata = {
    val catalogURI = Try(ConfigFactory.load().getString("server.catalog")).toOption
    val as: AttributeStore = catalogURI match {
      case Some(uri) => AttributeStore(uri)
      case None => throw new IllegalArgumentException("""Must specify a value for "server.catalog" in application.conf""")
    }

    println(s"Loading metadata for catalog at ${catalogURI.get} ...")
    as
      .layerIds
      .sortWith{ (a, b) => a.name < b.name || (a.name == b.name && a.zoom > b.zoom) }
      .groupBy(_.name)
      .mapValues(_.map(_.zoom))
      .map{ case (name, zooms) => {
        println(s"  -> $name @ zoom=${zooms.head}")
        val metadata = Try(as.readMetadata[TileLayerMetadata[SpatialKey]](LayerId(name, zooms.head))).toOption
        name -> (zooms, metadata)
      }}
  }

  def root =
    get { ctx =>
      extractUri { uri =>
        println(s"Request received: $uri")
        parameterMultiMap { params =>
          WCSParams(params) match {

            case Invalid(errors) =>
              complete {
                val msg = WCSParamsError.generateErrorMessage(errors.toList)
                println(s"""Error parsing parameters: ${msg}""")
                s"""Error parsing parameters: ${msg}"""
              }
            case Valid(wcsParams) =>
              wcsParams match {
                case p: GetCapabilitiesWCSParams =>
                  val link = s"${uri.scheme}://${uri.authority}${uri.path}?"
                  println(s"GetCapabilities request arrived at $link")
                  complete {
                    GetCapabilities.build(link, catalogMetadata, p)
                  }
                case p: DescribeCoverageWCSParams =>
                  println(s"DescribeCoverage request arrived at $uri")
                  complete {
                    DescribeCoverage.build(catalogMetadata, p)
                  }
                case p: GetCoverageWCSParams =>
                  println(s"GetCoverage request arrived at $uri")
                  complete {
                    GetCoverage.build(catalogMetadata, p)
                  }
              }
          }
        }
      }(ctx)
    }
}
