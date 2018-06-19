import sbt._

object Dependencies {
  val scalaVer         = "2.11.12"
  val gtServerVer      = "0.1.0"
  val http4sVer        = "0.18.11"
  val gtVer            = "2.0.0-RC1"
  val circeVer         = "0.10.0-M1"
  val pureConfVer      = "0.9.1"

  val scalaXml          = "org.scala-lang.modules"        %% "scala-xml"             % "1.1.0"
  val spark             = "org.apache.spark"              %% "spark-core"            % "2.3.0" % "provided"
  val hadoop            = "org.apache.hadoop"             %  "hadoop-client"         % "2.8.0" % "provided"
  val commonsIO         = "commons-io"                    %  "commons-io"            % "2.6"
  val geotrellisS3      = "org.locationtech.geotrellis"   %% "geotrellis-s3"         % gtVer
  val geotrellisSpark   = "org.locationtech.geotrellis"   %% "geotrellis-spark"      % gtVer
  val decline           = "com.monovore"                  %% "decline"               % "0.4.0"
  val cats              = "org.typelevel"                 %% "cats-core"             % "1.1.0"
  val circeCore         = "io.circe"                      %% "circe-core"            % circeVer
  val circeGeneric      = "io.circe"                      %% "circe-generic"         % circeVer
  val circeParser       = "io.circe"                      %% "circe-parser"          % circeVer
  val typesafeLogging   = "com.typesafe.scala-logging"    %% "scala-logging"         % "3.5.0"
  val logbackClassic    = "ch.qos.logback"                %  "logback-classic"       % "1.2.3"
  val caffeine          = "com.github.ben-manes.caffeine" % "caffeine"               % "2.6.2"
  val scaffeine         = "com.github.blemale"            %% "scaffeine"             % "2.5.0"
  val guava             = "com.google.guava"              %  "guava"                 % "25.0-jre"
  val http4sBlaze       = "org.http4s"                    %% "http4s-blaze-server"   % http4sVer
  val http4sDsl         = "org.http4s"                    %% "http4s-dsl"            % http4sVer
  val http4sCirce       = "org.http4s"                    %% "http4s-circe"          % http4sVer
  val http4sXml         = "org.http4s"                    %% "http4s-scala-xml"      % http4sVer
  val memcachedClient   = "net.spy"                       %  "spymemcached"          % "2.12.3"
  val pureConfigEnum    = "com.github.pureconfig"         %% "pureconfig-enumeratum" % pureConfVer
  val pureConfig        = "com.github.pureconfig"         %% "pureconfig"            % pureConfVer
  //val kamonCore       = "io.kamon"                      %% "kamon-core"            % "1.1.2"
  //val kamonCore       = "io.kamon"                      %% "kamon-system-metrics"  % "1.0.1"
  //val kamonCore       = "io.kamon"                      %% "kamon-prometheus"      % "1.0.0"
  //val kamonCore       = "io.kamon"                      %% "kamon-http4s"          % "1.0.7"
  //val kamonCore       = "io.kamon"                      %% "kamon-zipkin"          % "1.0.1"
  val scalatest         = "org.scalatest"                 %%  "scalatest"            % "3.0.4" % "test"
}
