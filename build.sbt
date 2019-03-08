import Dependencies._
import microsites._

scalaVersion := scalaVer
scalaVersion in ThisBuild := scalaVer

lazy val commonSettings = Seq(
  organization := "com.azavea",
  licenses += ("Apache-2.0", url("http://www.apache.org/licenses/LICENSE-2.0.txt")),
  cancelable in Global := true,
  scalaVersion := scalaVer,
  crossScalaVersions := crossScalaVer,
  scalacOptions := Seq(
    "-deprecation",
    "-unchecked",
    "-feature",
    "-language:implicitConversions",
    "-language:reflectiveCalls",
    "-language:higherKinds",
    "-language:postfixOps",
    "-language:existentials",
    "-language:experimental.macros",
    "-feature",
    "-Ypartial-unification",
    "-Ypatmat-exhaust-depth", "100",
    "-Xmacro-settings:materialize-derivations"
  ),
  resolvers ++= Seq(
    Resolver.bintrayRepo("bkirwi", "maven"), // Required for `decline` dependency
    Resolver.bintrayRepo("azavea", "maven"),
    Resolver.bintrayRepo("azavea", "geotrellis"),
    Resolver.sonatypeRepo("releases"),
    Resolver.sonatypeRepo("snapshots"),
    "osgeo" at "http://download.osgeo.org/webdav/geotools/",
    "locationtech-releases" at "https://repo.locationtech.org/content/groups/releases",
    "locationtech-snapshots" at "https://repo.locationtech.org/content/groups/snapshots",
    "geotrellis-staging" at "https://oss.sonatype.org/service/local/repositories/orglocationtechgeotrellis-1009/content"
  ),
  addCompilerPlugin(kindProjector cross CrossVersion.binary),
  addCompilerPlugin(macrosParadise cross CrossVersion.full),
  shellPrompt := { s => Project.extract(s).currentProject.id + " > " },
  fork := true,
  outputStrategy := Some(StdoutOutput),
  test in assembly := {},
  sources in (Compile, doc) := (sources in (Compile, doc)).value,
  assemblyMergeStrategy in assembly := {
    case "reference.conf" => MergeStrategy.concat
    case "application.conf" => MergeStrategy.concat
    case n if n.endsWith(".SF") || n.endsWith(".RSA") || n.endsWith(".DSA") => MergeStrategy.discard
    case "META-INF/MANIFEST.MF" => MergeStrategy.discard
    case _ => MergeStrategy.first
  },
  javaOptions ++= Seq("-Djava.library.path=/usr/local/lib")
)

lazy val publishSettings = Seq(
  bintrayOrganization := Some("azavea"),
  bintrayRepository := "geotrellis",
  bintrayVcsUrl := Some("https://github.com/geotrellis/geotrellis-server.git"),
  publishMavenStyle := true,
  publishArtifact in Test := false,
  pomIncludeRepository := { _ => false },
  homepage := Some(url("https://geotrellis.github.io/geotrellis-server"))
)

lazy val noPublishSettings = Seq(
  skip in publish := true,
  publish := {},
  publishLocal := {},
  publishArtifact := false
)

lazy val docsMappingsAPIDir = settingKey[String]("Name of subdirectory in site target directory for api docs")


lazy val docSettings = Seq(
  micrositeName := "GeoTrellis Server",
  micrositeDescription := "Expressive, modular, raster processing pipelines",
  micrositeAuthor := "the GeoTrellis team at Azavea",
  micrositeGitterChannel := false,
  micrositeOrganizationHomepage := "https://www.azavea.com/",
  micrositeGithubOwner := "geotrellis",
  micrositeGithubRepo := "geotrellis-server",
  micrositeBaseUrl := "/gtserver",
  micrositeDocumentationUrl := "/gtserver/latest/api",
  micrositeExtraMdFiles := Map(
    file("README.md") -> ExtraMdFileConfig(
      "index.md",
      "home",
      Map("title" -> "Home", "section" -> "home", "position" -> "0")
    )
  ),
  micrositeFooterText := Some(
    """
      |<p>© 2017 <a href="https://geotrellis.io/">GeoTrellis</a></p>
      |<p style="font-size: 80%; margin-top: 10px">Website built with <a href="https://47deg.github.io/sbt-microsites/">sbt-microsites © 2016 47 Degrees</a></p>
      |""".stripMargin)
)

lazy val root = project.in(file("."))
  .settings(moduleName := "root")
  .settings(commonSettings)
  .settings(noPublishSettings)
  .aggregate(core, example, docs)

lazy val core = project
  .settings(moduleName := "geotrellis-server-core")
  .settings(commonSettings)
  .settings(publishSettings)
  .settings(
    assemblyJarName in assembly := "geotrellis-server-core.jar",
    libraryDependencies ++= Seq(
      circeCore,
      circeGeneric,
      circeParser,
      circeOptics,
      circeShapes,
      geotrellisS3,
      geotrellisSpark,
      spark,
      geotrellisVlm,
      cats,
      catsEffect,
      mamlJvm,
      simulacrum,
      typesafeLogging,
      scalatest
    )
  )

lazy val example = project
  .settings(commonSettings)
  .settings(publishSettings)
  .dependsOn(core)
  .settings(
    moduleName := "geotrellis-server-example",
    assemblyJarName in assembly := "geotrellis-server-example.jar",
    libraryDependencies ++= Seq(
      http4sDsl,
      http4sBlazeServer,
      http4sBlazeClient,
      http4sCirce,
      http4sXml,
      scalaXml,
      geotrellisS3,
      geotrellisSpark,
      spark,
      decline,
      commonsIO,
      concHashMap,
      pureConfig,
      typesafeLogging,
      scalatest
    )
  )

lazy val ogc = project
  .dependsOn(core)
  .enablePlugins(ScalaxbPlugin)
  .enablePlugins(DockerPlugin)
  .settings(moduleName := "geotrellis-server-ogc")
  .settings(commonSettings)
  .settings(publishSettings)
  .settings(
    scalaxbDispatchVersion in (Compile, scalaxb)     := dispatchVer,
    scalaxbPackageName in (Compile, scalaxb)         := "generated",
    scalaxbProtocolPackageName in scalaxb in Compile := Some("opengis"),
    scalaxbPackageNames in scalaxb in Compile := Map(
      uri("http://www.w3.org/1999/xlink")           -> "xlink",
      uri("http://www.opengis.net/wms")             -> "opengis.wms",
      uri("http://www.opengis.net/ogc")             -> "opengis.ogc",
      uri("http://www.opengis.net/wmts/1.0")        -> "opengis.wmts",
      uri("http://www.opengis.net/ows/1.1")         -> "opengis.ows",
      uri("http://www.opengis.net/gml")             -> "opengis.gml",
      uri("http://www.w3.org/2001/SMIL20/")         -> "opengis.gml.smil",
      uri("http://www.w3.org/2001/SMIL20/Language") -> "opengis.gml.smil"
    )
  )
  .settings(
    assemblyJarName in assembly := "geotrellis-server-ogc.jar",
    libraryDependencies ++= Seq(
      http4sDsl,
      http4sBlazeServer,
      http4sBlazeClient,
      http4sCirce,
      http4sXml,
      spark,
      geotrellisS3,
      geotrellisSpark,
      geotrellisVlm,
      typesafeLogging,
      commonsIo, // to make GeoTiffRasterSources work
      slf4jApi, // enable logging
      slf4jSimple,
      http4sBlazeServer % Test,
      pureConfig,
      scaffeine,
      scalatest
    )
  )

lazy val docs = project
  .enablePlugins(MicrositesPlugin)
  .enablePlugins(SiteScaladocPlugin)
  .settings(moduleName := "geotrellis-server-docs")
  .settings(commonSettings)
  .settings(docSettings)
  .settings(noPublishSettings)
  .dependsOn(core, example)

lazy val bench = project
  .dependsOn(core)
  .settings(commonSettings)
  .enablePlugins(JmhPlugin)
