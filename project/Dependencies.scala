import sbt._

object Dependencies {

  val circeVer         = "0.10.0-M1"
  val gtVer            = "2.0.0-RC1"
  val gtServerVer      = "0.1.0"
  val http4sVer        = "0.18.12"
  val scalaVer         = "2.11.12"
  val tsecV            = "0.0.1-M11"

  //val kamonZipkin       = "io.kamon"                      %% "kamon-zipkin"         % "1.0.1"
  val caffeine          = "com.github.ben-manes.caffeine" %  "caffeine"             % "2.3.5"
  val cats              = "org.typelevel"                 %% "cats-core"            % "1.1.0"
  val catsEffect        = "org.typelevel"                 %% "cats-effect"          % "0.10.1"
  val circeCore         = "io.circe"                      %% "circe-core"           % circeVer
  val circeGeneric      = "io.circe"                      %% "circe-generic"        % circeVer
  val circeOptics       = "io.circe"                      %% "circe-optics"         % circeVer
  val circeParser       = "io.circe"                      %% "circe-parser"         % circeVer
  val commonsIO         = "commons-io"                    %  "commons-io"           % "2.6"
  val concHashMap       = "com.googlecode.concurrentlinkedhashmap" % "concurrentlinkedhashmap-lru" % "1.4.2"
  val decline           = "com.monovore"                  %% "decline"              % "0.4.0"
  val geotrellisS3      = "org.locationtech.geotrellis"   %% "geotrellis-s3"        % gtVer
  val geotrellisSpark   = "org.locationtech.geotrellis"   %% "geotrellis-spark"     % gtVer
  val hadoop            = "org.apache.hadoop"             %  "hadoop-client"        % "2.8.0" % "provided"
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
  val logbackClassic    = "ch.qos.logback"                %  "logback-classic"      % "1.2.3"
  val mamlJvm           = "com.azavea"                    %% "maml-jvm"             % "0.0.4-0fbf423"
  val pureConfig        = "com.github.pureconfig"         %% "pureconfig"           % "0.9.1"
  val scaffeine         = "com.github.blemale"            %% "scaffeine"            % "2.0.0"
  val scalaXml          = "org.scala-lang.modules"        %% "scala-xml"            % "1.1.0"
  val scalatest         = "org.scalatest"                 %%  "scalatest"           % "3.0.4" % "test"
  val simulacrum        = "com.github.mpilquist"          %% "simulacrum"           % "0.12.0"
  val spark             = "org.apache.spark"              %% "spark-core"           % "2.3.0" % "provided"
  val tsecCommon        = "io.github.jmcardon"            %% "tsec-common"          % tsecV
  val tsecHttp4s        = "io.github.jmcardon"            %% "tsec-http4s"          % tsecV
  val typesafeLogging   = "com.typesafe.scala-logging"    %% "scala-logging"        % "3.9.0"
}
