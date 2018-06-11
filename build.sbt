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
    "locationtech-releases" at "https://repo.locationtech.org/content/groups/releases",
    "locationtech-snapshots" at "https://repo.locationtech.org/content/groups/snapshots"
  ),
  addCompilerPlugin("org.spire-math" % "kind-projector" % "0.9.4" cross CrossVersion.binary),
  addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full),
  shellPrompt := { s => Project.extract(s).currentProject.id + " > " },
  fork := true
)

lazy val root =
  Project("root", file("."))
    .aggregate(
      core,
      server
    )
    .settings(commonSettings: _*)

lazy val core =
  Project(id = "core", base = file("core"))
    .settings(commonSettings: _*)
    .settings(
      name := "geotrellis-server-core"
    )

lazy val server =
  Project(id = "server", base = file("server"))
    .settings(commonSettings: _*)
    .dependsOn(core)
    .settings(
      name := "geotrellis-server",
      libraryDependencies ++= Seq(
        http4sDsl,
        http4sBlaze,
        http4sCirce,
        http4sXml,
        scalaXml,
        geotrellisS3,
        decline,
        cats
      )
    )
