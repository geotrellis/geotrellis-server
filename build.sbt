import Dependencies._

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
    "locationtech-releases" at "https://repo.locationtech.org/content/groups/releases",
    "locationtech-snapshots" at "https://repo.locationtech.org/content/groups/snapshots"
  ),
  addCompilerPlugin("org.spire-math" % "kind-projector" % "0.9.4" cross CrossVersion.binary),
  addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full),
  shellPrompt := { s => Project.extract(s).currentProject.id + " > " },
  fork := true,
  assemblyMergeStrategy in assembly := {
    case "reference.conf" => MergeStrategy.concat
    case "application.conf" => MergeStrategy.concat
    case n if n.endsWith(".SF") || n.endsWith(".RSA") || n.endsWith(".DSA") => MergeStrategy.discard
    case "META-INF/MANIFEST.MF" => MergeStrategy.discard
    case _ => MergeStrategy.first
  }
)

lazy val root =
  Project("root", file("."))
    .aggregate(
      core,
      http4s
    )
    .settings(commonSettings: _*)

lazy val core =
  Project(id = "core", base = file("core"))
    .settings(commonSettings: _*)
    .settings(
      name := "geotrellis-server-core",
      assemblyJarName in assembly := "geotrellis-server-core.jar",
      libraryDependencies ++= Seq(
        circeCore,
        circeGeneric,
        circeParser,
        circeOptics,
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

lazy val http4s =
  Project(id = "http4s", base = file("http4s"))
    .settings(commonSettings: _*)
    .dependsOn(core)
    .settings(
      name := "geotrellis-server-http4s",
      assemblyJarName in assembly := "geotrellis-server-http4s.jar",
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
        cats,
        commonsIO,
        concHashMap,
        kindProjector,
        pureConfig,
        typesafeLogging,
        logbackClassic,
        kamonCore,
        kamonSysMetrics,
        kamonPrometheus,
        kamonHttp4s,
        scalatest,
        tsecCommon,
        tsecHttp4s
      )
    )

