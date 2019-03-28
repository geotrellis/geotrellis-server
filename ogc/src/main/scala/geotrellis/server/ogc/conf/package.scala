package geotrellis.server.ogc

import geotrellis.server.ogc.wms.wmsScope

import geotrellis.proj4.CRS
import geotrellis.vector.Extent
import geotrellis.raster.TileLayout
import geotrellis.raster.render.{ColorMap, ColorRamp}

import com.azavea.maml.ast._
import com.azavea.maml.ast.codec.tree._

import io.circe.parser._
import pureconfig._

import scala.util.Try

/** Grab bag of [[ConfigReader]] instances necessary to read the configuration */
package object conf {

  implicit def colorRampReader: ConfigReader[ColorRamp] =
    ConfigReader[List[String]].map { colors =>
      ColorRamp(colors.map(java.lang.Long.decode(_).toInt))
    }

  implicit def colormapReader: ConfigReader[ColorMap] =
    ConfigReader[Map[String, String]].map { cmap =>
      ColorMap(cmap.map { case (k, v) => (k.toDouble -> java.lang.Long.decode(v).toInt) }.toMap)
    }

  implicit def keywordConfigReader: ConfigReader[opengis.wms.Keyword] =
    ConfigReader[String].map { str =>
      opengis.wms.Keyword(str)
    }

  implicit def nameConfigReader: ConfigReader[opengis.wms.Name] =
    ConfigReader[String].map { str =>
      opengis.wms.Name.fromString(str, wmsScope)
    }

  implicit val expressionReader: ConfigReader[Expression] =
    ConfigReader[String].map { expressionString =>
      decode[Expression](expressionString) match {
        case Right(success) =>
          success
        case Left(err) =>
          throw err
      }
    }

  implicit val crsReader: ConfigReader[CRS] =
    ConfigReader[Int].map { epsgCode =>
      Try(CRS.fromEpsgCode(epsgCode)).toOption match {
        case Some(crs) => crs
        case None => throw new Exception(s"Invalid EPSG code: ${epsgCode}")
      }
    }

  implicit val extentReader: ConfigReader[Extent] =
    ConfigReader[(Double, Double, Double, Double)].map { case extent@(xmin, ymin, xmax, ymax) =>
      Try(Extent(xmin, ymin, xmax, ymax)).toOption match {
        case Some(extent) => extent
        case None => throw new Exception(s"Invalid extent: ${extent}. Should be (xmin, ymin, xmax, ymax)")
      }
    }

  implicit val tileLayoutReader: ConfigReader[TileLayout] =
    ConfigReader[(Int, Int, Int, Int)].map { case layout@(layoutCols, layoutRows, tileCols, tileRows) =>
      Try(TileLayout(layoutCols, layoutRows, tileCols, tileRows)).toOption match {
        case Some(layout) => layout
        case None => throw new Exception(s"Invalid layout: ${layout}. Should be (layoutCols, layoutRows, tileCols, tileRows)")
      }
    }

  /** An alternative AST reading strategy that uses a separate json file */

  //private lazy val s3client = AmazonS3ClientBuilder.defaultClient()

  //def readString(uri: URI): String = uri.getScheme match {
  //  case "http" | "https" =>
  //    //Http(uri.toString).method("GET").asString.body
  //    throw new Exception("http-backed maml is not supported at this time, please use s3 or filesystem storage")
  //  case "file" =>
  //    Source.fromFile(uri.getPath).getLines.mkString
  //  case "s3" =>
  //    val s3uri = new AmazonS3URI(uri)
  //    val objectIS = s3client
  //      .getObject(s3uri.getBucket, s3uri.getKey)
  //      .getObjectContent()
  //    // found this method for IS => String from: https://stackoverflow.com/questions/309424/how-do-i-read-convert-an-inputstream-into-a-string-in-java
  //    new BufferedReader(new InputStreamReader(objectIS))
  //      .lines()
  //      .collect(Collectors.joining("\n"));
  //  case _ =>
  //    throw new Exception(
  //      "A valid URI is required...")
  //}

  //  implicit val expressionReader: ConfigReader[Expression] =
  //  ConfigReader[URI].map { expressionURI =>
  //    val expressionString = readString(expressionURI)
  //    decode[Expression](expressionString) match {
  //      case Right(expr) => expr
  //      case Left(err) => throw err
  //    }
  //  }
}
