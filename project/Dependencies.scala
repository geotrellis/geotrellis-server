import sbt._
import sbt.Keys._

object Dependencies {

  def catsVersion(module: String) = Def.setting {
    val version = module match {
      case "core"    => "2.10.0"
      case "effect"  => "3.5.4"
      case "tagless" => "0.16.0"
    }

    "org.typelevel" %% s"cats-$module" % version
  }

  def circeVersion(module: String) = Def.setting {
    val version = module match {
      case "generic-extras" => "0.14.3"
      case _                => "0.14.7"
    }

    "io.circe" %% s"circe-$module" % version
  }
  def http4sVer(module: String) = Def.setting {
    val version = module match {
      case "scala-xml" => "0.23.13"
      case "circe"     => "0.23.27"
      case "dsl"       => "0.23.27"
      case _           => "0.23.16"
    }
    "org.http4s" %% s"http4s-$module" % version
  }

  val crossScalaVer = List("2.13.14", "2.12.19")
  val scalaVer = crossScalaVer.head

  val dispatchVer = "0.11.3"
  val gtVer = "3.7.1"
  val stac4sVer = "0.9.1"
  val jaxbApiVer = "2.3.1"
  val refinedVer = "0.11.1"
  val shapelessVer = "2.3.3"

  val cats = catsVersion("core")
  val catsEffect = catsVersion("effect")
  val catsTagless = catsVersion("tagless")
  val circeCore = circeVersion("core")
  val circeShapes = circeVersion("shapes")
  val circeGeneric = circeVersion("generic")
  val circeGenericExtras = circeVersion("generic-extras")
  val circeOptics = Def.setting("io.circe" %% "circe-optics" % "0.14.1")
  val circeParser = circeVersion("parser")
  val circeRefined = circeVersion("refined")
  val circeJava8 = circeVersion("java8")
  val commonsIO = "commons-io" % "commons-io" % "2.16.1"
  val concHashMap = "com.googlecode.concurrentlinkedhashmap" % "concurrentlinkedhashmap-lru" % "1.4.2"
  val decline = "com.monovore" %% "decline" % "2.4.1"
  val geotrellisRaster = "org.locationtech.geotrellis" %% "geotrellis-raster" % gtVer
  val geotrellisLayer = "org.locationtech.geotrellis" %% "geotrellis-layer" % gtVer
  val geotrellisVector = "org.locationtech.geotrellis" %% "geotrellis-vector" % gtVer
  val geotrellisS3 = "org.locationtech.geotrellis" %% "geotrellis-s3" % gtVer
  val geotrellisStore = "org.locationtech.geotrellis" %% "geotrellis-store" % gtVer
  val geotrellisHBase = "org.locationtech.geotrellis" %% "geotrellis-hbase" % gtVer
  val geotrellisAccumulo = "org.locationtech.geotrellis" %% "geotrellis-accumulo" % gtVer
  val geotrellisCassandra = "org.locationtech.geotrellis" %% "geotrellis-cassandra" % gtVer
  val geotrellisGdal = "org.locationtech.geotrellis" %% "geotrellis-gdal" % gtVer
  val http4sBlazeClient = http4sVer("blaze-client")
  val http4sBlazeServer = http4sVer("blaze-server")
  val http4sCirce = http4sVer("circe")
  val http4sDsl = http4sVer("dsl")
  val http4sXml = http4sVer("scala-xml")
  val jaxbApi = "javax.xml.bind" % "jaxb-api" % jaxbApiVer
  val kindProjector = "org.typelevel" %% "kind-projector" % "0.13.3"
  val semanticdbScalac = "org.scalameta" % "semanticdb-scalac" % "4.9.4"
  val log4cats = "org.typelevel" %% "log4cats-slf4j" % "2.7.0"
  val mamlJvm = "com.azavea.geotrellis" %% "maml-jvm" % "0.7.0"
  val pureConfig = "com.github.pureconfig" %% "pureconfig" % "0.17.6"
  val pureConfigCatsEffect = "com.github.pureconfig" %% "pureconfig-cats-effect" % "0.17.6"
  val scaffeine = "com.github.blemale" %% "scaffeine" % "5.2.1"
  val scalaXml = "org.scala-lang.modules" %% "scala-xml" % "2.3.0"
  val scalatest = "org.scalatest" %% "scalatest" % "3.2.18" % Test
  val scalacheck = "org.scalacheck" %% "scalacheck" % "1.14.0" % Test
  val scalacheckCats = "io.chrisdavenport" %% "cats-scalacheck" % "0.1.1" % Test
  val sttpHttp4s = "com.softwaremill.sttp.client3" %% "http4s-backend" % "3.9.6"
  val macrosParadise = "org.scalamacros" % "paradise" % "2.1.1"
  val scalaParser = "org.scala-lang.modules" %% "scala-parser-combinators" % "2.4.0"
  val shapeless = "com.chuusai" %% "shapeless" % shapelessVer
  val logback = "ch.qos.logback" % "logback-classic" % "1.5.6" % Runtime
  val droste = "io.higherkindness" %% "droste-core" % "0.9.0"
  val stac4sCore = "com.azavea.stac4s" %% "core" % stac4sVer
  val stac4sClient = "com.azavea.stac4s" %% "client" % stac4sVer
  val refinedCats = "eu.timepit" %% "refined-cats" % refinedVer
  val refinedPureconfig = "eu.timepit" %% "refined-pureconfig" % refinedVer
  val threetenExtra = "org.threeten" % "threeten-extra" % "1.8.0"
  val ansiColors212 = "org.backuity" %% "ansi-interpolator" % "1.1.0" % Provided
  val tofuCore = "tf.tofu" %% "tofu-core-ce3" % "0.13.0"
  val azureStorage = "com.azure" % "azure-storage-blob" % "12.25.4"
}
