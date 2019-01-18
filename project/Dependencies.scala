import sbt._

object Dependencies {

  val circeVer         = "0.10.0"
  val gtVer            = "3.0.0-SNAPSHOT"
  val gtcVer           = "0.7.13"
  val http4sVer        = "0.19.0"
  val scalaVer         = "2.11.12"
  val crossScalaVer    = Seq(scalaVer, "2.12.7")
  val tsecVer          = "0.0.1-M11"
  val dispatchVer      = "0.11.3"

  //val kamonZipkin       = "io.kamon"                      %% "kamon-zipkin"         % "1.0.1"
  val caffeine          = "com.github.ben-manes.caffeine" %  "caffeine"             % "2.3.5"
  val cats              = "org.typelevel"                 %% "cats-core"            % "1.4.0"
  val catsEffect        = "org.typelevel"                 %% "cats-effect"          % "1.0.0"
  val circeCore         = "io.circe"                      %% "circe-core"           % circeVer
  val circeShapes       = "io.circe"                      %% "circe-shapes"         % circeVer
  val circeGeneric      = "io.circe"                      %% "circe-generic"        % circeVer
  val circeOptics       = "io.circe"                      %% "circe-optics"         % circeVer
  val circeParser       = "io.circe"                      %% "circe-parser"         % circeVer
  val commonsIO         = "commons-io"                    %  "commons-io"           % "2.6"
  val commonsLang       = "org.apache.commons"            %  "commons-lang3"        % "3.7"
  val concHashMap       = "com.googlecode.concurrentlinkedhashmap" % "concurrentlinkedhashmap-lru" % "1.4.2"
  val decline           = "com.monovore"                  %% "decline"              % "0.4.0"
  val geotrellisS3      = "org.locationtech.geotrellis"   %% "geotrellis-s3"        % gtVer
  val geotrellisSpark   = "org.locationtech.geotrellis"   %% "geotrellis-spark"     % gtVer
  val geotrellisVlm     = "com.azavea.geotrellis"         %% "geotrellis-contrib-vlm" % gtcVer
  val hadoop            = "org.apache.hadoop"             %  "hadoop-client"        % "2.8.0" % Provided
  val http4sBlazeClient = "org.http4s"                    %% "http4s-blaze-client"  % http4sVer
  val http4sBlazeServer = "org.http4s"                    %% "http4s-blaze-server"  % http4sVer
  val http4sCirce       = "org.http4s"                    %% "http4s-circe"         % http4sVer
  val http4sDsl         = "org.http4s"                    %% "http4s-dsl"           % http4sVer
  val http4sXml         = "org.http4s"                    %% "http4s-scala-xml"     % http4sVer
  val kamonCore         = "io.kamon"                      %% "kamon-core"           % "1.1.3"
  val kamonHttp4s       = "io.kamon"                      %% "kamon-http4s"         % "1.0.7"
  val kamonPrometheus   = "io.kamon"                      %% "kamon-prometheus"     % "1.0.0"
  val kamonSysMetrics   = "io.kamon"                      %% "kamon-system-metrics" % "1.0.0"
  val kindProjector     = "org.spire-math"                %% "kind-projector"       % "0.9.4"
  val mamlJvm           = "com.azavea"                    %% "maml-jvm"             % "0.2.0"
  // Note: pureconfig is not yet stable, version 0.10.0 is not binary copatible with 0.9.2 which is used by GT
  val pureConfig        = "com.github.pureconfig"         %% "pureconfig"           % "0.9.2"
  val scaffeine         = "com.github.blemale"            %% "scaffeine"            % "2.0.0"
  val scalaXml          = "org.scala-lang.modules"        %% "scala-xml"            % "1.1.0"
  val scalatest         = "org.scalatest"                 %%  "scalatest"           % "3.0.4" % Test
  val simulacrum        = "com.github.mpilquist"          %% "simulacrum"           % "0.12.0"
  val spark             = "org.apache.spark"              %% "spark-core"           % "2.4.0" % Provided
  val tsecCommon        = "io.github.jmcardon"            %% "tsec-common"          % tsecVer
  val tsecHttp4s        = "io.github.jmcardon"            %% "tsec-http4s"          % tsecVer
  val typesafeLogging   = "com.typesafe.scala-logging"    %% "scala-logging"        % "3.9.0"
  val macrosParadise    = "org.scalamacros"                % "paradise"             % "2.1.0"
  val scalaParser       = "org.scala-lang.modules"        %% "scala-parser-combinators" % "1.1.1"
  val dispatch          = "net.databinder.dispatch"       %% "dispatch-core"        % dispatchVer
  val apacheMath        = "org.apache.commons"             % "commons-math3"        % "3.6.1"
  val commonsIo         = "commons-io"                     % "commons-io"           % "2.6"
  val slf4jApi          = "org.slf4j"                      % "slf4j-api"            % "1.7.25"
  val slf4jSimple       = "org.slf4j"                      % "slf4j-simple"         % "1.7.25"
}
