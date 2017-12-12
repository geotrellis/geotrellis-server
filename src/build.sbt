import Dependencies._

lazy val commonSettings = Seq(
  organization := "com.azavea",
  version := Version.geotrellisServer,
  cancelable in Global := true,
  scalaVersion := Version.scala,
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
    "locationtech-releases" at "https://repo.locationtech.org/content/repositories/releases/"
  ),
  addCompilerPlugin("org.spire-math" % "kind-projector" % "0.9.4" cross CrossVersion.binary),
  addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full),
  shellPrompt := { s => Project.extract(s).currentProject.id + " > " }
)

lazy val root =
  Project("root", file("."))
    .aggregate(
      core,
      server
    )
    .settings(commonSettings: _*)

lazy val core =
  Project(id = "core", base = file("core")).
    settings(commonSettings: _*)

lazy val server =
  Project(id = "server", base = file("server"))
    .settings(commonSettings: _*)
    .dependsOn(core)
