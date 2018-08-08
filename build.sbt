import Dependencies._
import microsites._

scalaVersion := scalaVer
scalaVersion in ThisBuild := scalaVer

lazy val commonSettings = Seq(
  organization := "com.azavea",
  version := gtServerVer,
  cancelable in Global := true,
  scalaVersion := scalaVer,
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
    "-Ypatmat-exhaust-depth", "100"
  ),
  resolvers ++= Seq(
    Resolver.bintrayRepo("bkirwi", "maven"), // Required for `decline` dependency
    Resolver.bintrayRepo("azavea", "maven"),
    Resolver.sonatypeRepo("releases"),
    Resolver.sonatypeRepo("snapshots"),
    "locationtech-releases" at "https://repo.locationtech.org/content/groups/releases",
    "locationtech-snapshots" at "https://repo.locationtech.org/content/groups/snapshots"
  ),
  addCompilerPlugin("org.spire-math" % "kind-projector" % "0.9.4" cross CrossVersion.binary),
  addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full),
  shellPrompt := { s => Project.extract(s).currentProject.id + " > " },
  fork := true,
  test in assembly := {},
  sources in (Compile, doc) := (sources in (Compile, doc)).value,
  assemblyMergeStrategy in assembly := {
    case "reference.conf" => MergeStrategy.concat
    case "application.conf" => MergeStrategy.concat
    case n if n.endsWith(".SF") || n.endsWith(".RSA") || n.endsWith(".DSA") => MergeStrategy.discard
    case "META-INF/MANIFEST.MF" => MergeStrategy.discard
    case _ => MergeStrategy.first
  }
)

lazy val noPublishSettings = Seq(
  publish := {},
  publishLocal := {},
  publishArtifact := false
)

lazy val docsMappingsAPIDir = settingKey[String]("Name of subdirectory in site target directory for api docs")


lazy val docSettings = Seq(
  micrositeName := "GT Server",
  micrositeDescription := "Map Algebra Model Language",
  micrositeAuthor := "GeoTrellis Team at Azavea",
  micrositeGitterChannel := false,
  micrositeOrganizationHomepage := "https://www.azavea.com/",
  micrositeGithubOwner := "geotrellis",
  micrositeGithubRepo := "geotrellis-server",
  micrositeBaseUrl := "/gtserve",
  micrositeDocumentationUrl := "/gtserve/latest/api",
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
      cats,
      catsEffect,
      mamlJvm,
      kindProjector,
      simulacrum,
      typesafeLogging,
      logbackClassic,
      concHashMap
    )
  )

lazy val example = project
  .settings(commonSettings)
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
      decline,
      commonsIO,
      concHashMap,
      pureConfig,
      typesafeLogging,
      logbackClassic,
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


