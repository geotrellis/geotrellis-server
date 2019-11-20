import sbt._

object Dependencies {

  val scalaVer = "2.11.12"
  val crossScalaVer = Seq(scalaVer, "2.12.7")

  val circeVer = "0.11.1"
  val dispatchVer = "0.11.3"
  val gtVer = "3.1.0"
  val http4sVer = "0.20.0"
  val refinedVer = "0.9.9"
  val shapelessVer = "2.3.3"
  val spdxCheckerVer = "1.0.0"
  val sttpVer = "1.5.17"
  val tsecVer = "0.0.1-M11"

  val caffeine = "com.github.ben-manes.caffeine" % "caffeine" % "2.3.5"
  val cats = "org.typelevel" %% "cats-core" % "1.4.0"
  val catsEffect = "org.typelevel" %% "cats-effect" % "1.0.0"
  val circeCore = "io.circe" %% "circe-core" % circeVer
  val circeShapes = "io.circe" %% "circe-shapes" % circeVer
  val circeGeneric = "io.circe" %% "circe-generic" % circeVer
  val circeOptics = "io.circe" %% "circe-optics" % "0.11.0"
  val circeParser = "io.circe" %% "circe-parser" % circeVer
  val circeRefined = "io.circe" %% "circe-refined" % circeVer
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
  val http4sBlazeClient = "org.http4s" %% "http4s-blaze-client" % http4sVer
  val http4sBlazeServer = "org.http4s" %% "http4s-blaze-server" % http4sVer
  val http4sCirce = "org.http4s" %% "http4s-circe" % http4sVer
  val http4sDsl = "org.http4s" %% "http4s-dsl" % http4sVer
  val http4sXml = "org.http4s" %% "http4s-scala-xml" % http4sVer
  val kamonCore = "io.kamon" %% "kamon-core" % "1.1.3"
  val kamonHttp4s = "io.kamon" %% "kamon-http4s" % "1.0.7"
  val kamonPrometheus = "io.kamon" %% "kamon-prometheus" % "1.0.0"
  val kamonSysMetrics = "io.kamon" %% "kamon-system-metrics" % "1.0.0"
  val kindProjector = "org.spire-math" %% "kind-projector" % "0.9.4"
  val mamlJvm = "com.azavea.geotrellis" %% "maml-jvm" % "0.4.0"
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
