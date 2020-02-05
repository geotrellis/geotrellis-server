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
        "org.typelevel" %% s"cats-$module" % ver("1.6.1", "2.0.0").value
      case "effect" =>
        "org.typelevel" %% s"cats-$module" % ver("1.4.0", "2.0.0").value
    }
  }

  def circeVersion(module: String) = Def.setting {
    "io.circe" %% s"circe-$module" % ver("0.11.1", "0.12.2").value
  }

  def http4sVer(module: String) = Def.setting {
    "org.http4s" %% s"http4s-$module" % ver("0.20.15", "0.21.0-M6").value
  }

  val scalaVer = "2.11.12"
  val crossScalaVer = Seq(scalaVer, "2.12.10")

  val circeVer = "0.11.1"
  val dispatchVer = "0.11.3"
  val gtVer = "3.2.0"
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
  val commonsIO = "commons-io" % "commons-io" % "2.6"
  val commonsLang = "org.apache.commons" % "commons-lang3" % "3.7"
  val concHashMap = "com.googlecode.concurrentlinkedhashmap" % "concurrentlinkedhashmap-lru" % "1.4.2"
  val decline = "com.monovore" %% "decline" % "0.5.0"
  val geotrellisRaster = "org.locationtech.geotrellis" %% "geotrellis-raster" % gtVer
  val geotrellisVector = "org.locationtech.geotrellis" %% "geotrellis-vector" % gtVer
  val geotrellisS3 = "org.locationtech.geotrellis" %% "geotrellis-s3" % gtVer
  val geotrellisSpark = "org.locationtech.geotrellis" %% "geotrellis-spark" % gtVer
  val geotrellisHBase = "org.locationtech.geotrellis" %% "geotrellis-hbase" % gtVer
  val geotrellisAccumulo = "org.locationtech.geotrellis" %% "geotrellis-accumulo" % gtVer
  val geotrellisCassandra = "org.locationtech.geotrellis" %% "geotrellis-cassandra" % gtVer
  val geotrellisGdal = "org.locationtech.geotrellis" %% "geotrellis-gdal" % gtVer
  val hadoop = "org.apache.hadoop" % "hadoop-client" % "2.8.0" % Provided
  val http4sBlazeClient = http4sVer("blaze-client")
  val http4sBlazeServer = http4sVer("blaze-server")
  val http4sCirce = http4sVer("circe")
  val http4sDsl = http4sVer("dsl")
  val http4sXml = http4sVer("scala-xml")
  val jaxbApi = "javax.xml.bind" % "jaxb-api" % jaxbApiVer
  val kamonCore = "io.kamon" %% "kamon-core" % "1.1.3"
  val kamonHttp4s = "io.kamon" %% "kamon-http4s" % "1.0.7"
  val kamonPrometheus = "io.kamon" %% "kamon-prometheus" % "1.0.0"
  val kamonSysMetrics = "io.kamon" %% "kamon-system-metrics" % "1.0.0"
  val kindProjector = "org.typelevel" %% "kind-projector" % "0.11.0"
  val mamlJvm = "com.azavea.geotrellis" %% "maml-jvm" % "0.5.1-2-g0baee67-SNAPSHOT"
  val pureConfig = "com.github.pureconfig" %% "pureconfig" % "0.10.2"
  val refined = "eu.timepit" %% "refined" % refinedVer
  val scaffeine = "com.github.blemale" %% "scaffeine" % "2.6.0"
  val scalaXml = "org.scala-lang.modules" %% "scala-xml" % "1.1.0"
  val scalatest = "org.scalatest" %% "scalatest" % "3.0.4" % Test
  val scalacheck = "org.scalacheck" %% "scalacheck" % "1.14.0" % Test
  val scalacheckCats = "io.chrisdavenport" %% "cats-scalacheck" % "0.1.1" % Test
  val simulacrum = "com.github.mpilquist" %% "simulacrum" % "0.12.0"
  val spark = "org.apache.spark" %% "spark-core" % "2.4.0" % Provided
  val spdxChecker = "com.github.tbouron" % "spdx-license-checker" % spdxCheckerVer
  val sttp = "com.softwaremill.sttp" %% "core" % sttpVer
  val sttpCirce = "com.softwaremill.sttp" %% "circe" % sttpVer
  val sttpCats = "com.softwaremill.sttp" %% "async-http-client-backend-cats" % sttpVer
  val tsecCommon = "io.github.jmcardon" %% "tsec-common" % tsecVer
  val tsecHttp4s = "io.github.jmcardon" %% "tsec-http4s" % tsecVer
  val typesafeLogging = "com.typesafe.scala-logging" %% "scala-logging" % "3.9.0"
  val macrosParadise = "org.scalamacros" % "paradise" % "2.1.0"
  val scalaParser = "org.scala-lang.modules" %% "scala-parser-combinators" % "1.1.1"
  val dispatch = "net.databinder.dispatch" %% "dispatch-core" % dispatchVer
  val apacheMath = "org.apache.commons" % "commons-math3" % "3.6.1"
  val commonsIo = "commons-io" % "commons-io" % "2.6"
  val slf4jApi = "org.slf4j" % "slf4j-api" % "1.7.25"
  val slf4jSimple = "org.slf4j" % "slf4j-simple" % "1.7.25"
  val logback = "ch.qos.logback" % "logback-classic" % "1.1.7"
  val shapeless = "com.chuusai" %% "shapeless" % shapelessVer

  // This dependency differs between scala 2.11 and 2.12
  val ansiColors211 = "org.backuity" %% "ansi-interpolator" % "1.1" % Provided
  val ansiColors212 = "org.backuity" %% "ansi-interpolator" % "1.1.0" % Provided
}
