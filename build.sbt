import Dependencies._
import microsites._


addCommandAlias("bintrayPublish", ";publish;bintrayRelease")

scalaVersion := scalaVer
scalaVersion in ThisBuild := scalaVer
updateOptions := updateOptions.value.withCachedResolution(true)

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
    "locationtech-snapshots" at "https://repo.locationtech.org/content/groups/snapshots"
  ),
  updateOptions := updateOptions.value.withCachedResolution(true),
  addCompilerPlugin(kindProjector cross CrossVersion.binary),
  addCompilerPlugin(macrosParadise cross CrossVersion.full),
  shellPrompt := { s => Project.extract(s).currentProject.id + " > " },
  fork in run := true,
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
  bintrayReleaseOnPublish := false,
  publishTo := {
    val bintrayPublishTo = publishTo.value
    val nexus = "http://nexus.internal.azavea.com"

    if (isSnapshot.value) {
      Some("snapshots" at nexus + "/repository/azavea-snapshots")
    } else {
      bintrayPublishTo
    }
  },
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
  micrositeGithubRepo := "geotrellis-servern",
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
  .aggregate(core, example, docs, ogc, ogcExample, stac, opengis)

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
  .settings(noPublishSettings)
  .dependsOn(core, stac)
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
      geotrellisContribGDAL,
      spark,
      decline,
      commonsIO,
      concHashMap,
      pureConfig,
      typesafeLogging,
      sttp,
      sttpCats,
      sttpCirce,
      scalatest
    )
  ).settings(dependencyOverrides += "com.azavea.gdal" % "gdal-warp-bindings" % "33.5523882")

lazy val opengis = project
  .enablePlugins(ScalaxbPlugin)
  .settings(moduleName := "geotrellis-server-opengis")
  .settings(commonSettings)
  .settings(publishSettings)
  .settings(
    libraryDependencies ++= Seq(
      scalaXml,
      scalaParser
    )
  )
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

lazy val ogc = project
  .dependsOn(core, opengis)
  .settings(moduleName := "geotrellis-server-ogc")
  .settings(commonSettings)
  .settings(publishSettings)
  .settings(
    assemblyJarName in assembly := "geotrellis-server-ogc.jar",
    libraryDependencies ++= Seq(
      spark,
      geotrellisS3,
      geotrellisSpark,
      geotrellisVlm,
      typesafeLogging,
      commonsIo, // to make GeoTiffRasterSources work
      slf4jApi, // enable logging
      scaffeine,
      scalatest
    )
  )

lazy val ogcExample = (project in file("ogc-example"))
  .dependsOn(ogc)
  .enablePlugins(DockerPlugin)
  .settings(moduleName := "geotrellis-server-ogc-example")
  .settings(commonSettings)
  .settings(publishSettings)
  .settings(publish / skip := true)
  .settings(
    assemblyJarName in assembly := "geotrellis-server-ogc-services.jar",
    libraryDependencies ++= Seq(
      spark,
      geotrellisS3,
      geotrellisSpark,
      geotrellisCassandra,
      geotrellisHBase,
      geotrellisAccumulo,
      geotrellisVlm,
      http4sDsl,
      http4sBlazeServer,
      http4sBlazeClient,
      http4sCirce,
      http4sXml,
      logback,
      typesafeLogging,
      pureConfig,
      scaffeine,
      scalatest,
      decline
    ),
    excludeDependencies ++= Seq(
      // log4j brought in via uzaygezen is a pain for us
      ExclusionRule("log4j", "log4j")
    ),
    libraryDependencies := (CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, scalaMajor)) if scalaMajor >= 12 =>
        libraryDependencies.value ++ Seq(ansiColors212)
      case Some((2, scalaMajor)) if scalaMajor >= 11 =>
        libraryDependencies.value ++ Seq(ansiColors211)
    })
  )

lazy val stac = project
  .settings(moduleName := "geotrellis-server-stac")
  .settings(commonSettings)
  .settings(publishSettings)
  .settings(
    libraryDependencies ++= Seq(
      cats,
      circeCore,
      circeGeneric,
      circeParser,
      circeShapes,
      geotrellisS3,
      geotrellisVlm,
      shapeless,
      scalacheck,
      scalacheckCats,
      scalatest
    )
  )

lazy val docs = project
  .enablePlugins(MicrositesPlugin)
  .enablePlugins(SiteScaladocPlugin)
  .settings(moduleName := "geotrellis-server-docs")
  .settings(publish / skip := true)
  .settings(commonSettings)
  .settings(docSettings)
  .settings(noPublishSettings)
  .dependsOn(core, example)

lazy val bench = project
  .dependsOn(core)
  .settings(publish / skip := true)
  .settings(commonSettings)
  .enablePlugins(JmhPlugin)
