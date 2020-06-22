import sbt._
import sbt.Keys._

object Dependencies {

  private def ver(for211: String, for212: String) = Def.setting {
    CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, 11)) => for211
      case Some((2, 12)) => for212
      case _             => sys.error("not good")
    }
  }

  def catsVersion(module: String) = Def.setting {
    module match {
      case "core" =>
        "org.typelevel" %% s"cats-$module" % ver("1.6.1", "2.0.0").value force ()
      case "effect" =>
        "org.typelevel" %% s"cats-$module" % ver("1.4.0", "2.0.0").value force ()
    }
  }

  def circeVersion(module: String) = Def.setting {
    "io.circe" %% s"circe-$module" % ver("0.11.1", "0.12.2").value
  }

  def http4sVer(module: String) = Def.setting {
    "org.http4s" %% s"http4s-$module" % ver("0.20.15", "0.21.0-M6").value
  }

  val crossScalaVer = List("2.12.11")
  val scalaVer = crossScalaVer.head

  val circeVer = "0.11.1"
  val dispatchVer = "0.11.3"
  val gtVer = "3.4.0"
  val jaxbApiVer = "2.3.1"
  val refinedVer = "0.9.9"
  val shapelessVer = "2.3.3"
  val spdxCheckerVer = "1.0.0"
  val sttpVer = "1.5.17"
  val tsecVer = "0.0.1-M11"

  val caffeine = "com.github.ben-manes.caffeine" % "caffeine" % "2.3.5"
  val cats = catsVersion("core")
  val catsEffect = catsVersion("effect")
  val circeCore = circeVersion("core")
  val circeShapes = circeVersion("shapes")
  val circeGeneric = circeVersion("generic")
  val circeOptics = Def.setting {
    "io.circe" %% "circe-optics" % ver("0.11.0", "0.12.0").value
  }
  val circeParser = circeVersion("parser")
  val circeRefined = circeVersion("refined")
  val circeJava8 = circeVersion("java8")
  val commonsIO = "commons-io" % "commons-io" % "2.6"
  val commonsLang = "org.apache.commons" % "commons-lang3" % "3.7"
  val concHashMap = "com.googlecode.concurrentlinkedhashmap" % "concurrentlinkedhashmap-lru" % "1.4.2"
  val decline = "com.monovore" %% "decline" % "0.5.0"
  val geotrellisRaster = "org.locationtech.geotrellis" %% "geotrellis-raster" % gtVer
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
  val kindProjector = "org.typelevel" %% "kind-projector" % "0.11.0"
  val log4cats = "io.chrisdavenport" %% "log4cats-slf4j" % "1.1.1"
  val mamlJvm = "com.azavea.geotrellis" %% "maml-jvm" % "0.6.1"
  val pureConfig = "com.github.pureconfig" %% "pureconfig" % "0.12.2"
  val pureConfigCatsEffect = "com.github.pureconfig" %% "pureconfig-cats-effect" % "0.12.2"
  val refined = "eu.timepit" %% "refined" % refinedVer
  val scaffeine = "com.github.blemale" %% "scaffeine" % "2.6.0"
  val scalaXml = "org.scala-lang.modules" %% "scala-xml" % "1.1.0"
  val scalatest = "org.scalatest" %% "scalatest" % "3.0.4" % Test
  val scalacheck = "org.scalacheck" %% "scalacheck" % "1.14.0" % Test
  val scalacheckCats = "io.chrisdavenport" %% "cats-scalacheck" % "0.1.1" % Test
  val simulacrum = "com.github.mpilquist" %% "simulacrum" % "0.12.0"
  val spdxChecker = "com.github.tbouron" % "spdx-license-checker" % spdxCheckerVer
  val sttp = "com.softwaremill.sttp" %% "core" % sttpVer
  val sttpCirce = "com.softwaremill.sttp" %% "circe" % sttpVer
  val sttpCats = "com.softwaremill.sttp" %% "async-http-client-backend-cats" % sttpVer
  val tsecCommon = "io.github.jmcardon" %% "tsec-common" % tsecVer
  val macrosParadise = "org.scalamacros" % "paradise" % "2.1.1"
  val scalaParser = "org.scala-lang.modules" %% "scala-parser-combinators" % "1.1.1"
  val commonsIo = "commons-io" % "commons-io" % "2.6"
  val shapeless = "com.chuusai" %% "shapeless" % shapelessVer
  val logback = "ch.qos.logback" % "logback-classic" % "1.2.3" % Runtime
  val droste = "io.higherkindness" %% "droste-core" % "0.8.0"
  val stac4s = "com.azavea.stac4s" %% "core" % "0.0.10"
  val ansiColors212 = "org.backuity" %% "ansi-interpolator" % "1.1.0" % Provided
}
