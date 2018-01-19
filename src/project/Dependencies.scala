import sbt._

object Dependencies {
  val spark             = "org.apache.spark"            %% "spark-core"       % Version.spark % "provided"
  val hadoop            = "org.apache.hadoop"            % "hadoop-client"    % Version.hadoop % "provided"
  val commonsIO         = "commons-io"                   % "commons-io"       % "2.6"
  val geotrellisS3      = "org.locationtech.geotrellis" %% "geotrellis-s3"    % Version.geotrellis
  val geotrellisSpark   = "org.locationtech.geotrellis" %% "geotrellis-spark" % Version.geotrellis
  val decline           = "com.monovore"                %% "decline"          % Version.decline
  val ficus             = "com.iheart"                  %% "ficus"            % Version.ficus
  val scalatest         = "org.scalatest"               %%  "scalatest"       % Version.scalaTest % "test"
  val akka              = "com.typesafe.akka"           %% "akka-actor"       % Version.akka
  val akkaHttp          = "com.typesafe.akka"           %% "akka-http"        % Version.akka
  val akkaHttpXml       = "com.typesafe.akka"           %% "akka-http-xml"    % Version.akka
  val akkaCirceJson     = "de.heikoseeberger"           %% "akka-http-circe"  % Version.akkaCirceJson
  val cats              = "org.typelevel"               %% "cats-core"        % Version.cats
  val circeCore         = "io.circe"                    %% "circe-core"       % Version.circe
  val circeGeneric      = "io.circe"                    %% "circe-generic"    % Version.circe
  val circeParser       = "io.circe"                    %% "circe-parser"     % Version.circe
  val typesafeLogging   = "com.typesafe.scala-logging"  %% "scala-logging"    % Version.typesafeLogging
  val logbackClassic    = "ch.qos.logback"               % "logback-classic"  % Version.logbackClassic
}
