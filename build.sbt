import sbt.Keys._
import xerial.sbt.Sonatype._
import de.heikoseeberger.sbtheader._

import Dependencies._

scalaVersion := scalaVer
ThisBuild / scalaVersion := scalaVer

val currentYear = java.time.Year.now.getValue.toString

lazy val commonSettings = Seq(
  // We are overriding the default behavior of sbt-git which, by default,
  // only appends the `-SNAPSHOT` suffix if there are uncommitted
  // changes in the workspace.
  version := {
    if (git.gitHeadCommit.value.isEmpty) "0.0.1-SNAPSHOT"
    else if (git.gitDescribedVersion.value.isEmpty)
      git.gitHeadCommit.value.get.substring(0, 7) + "-SNAPSHOT"
    else if (git.gitCurrentTags.value.isEmpty || git.gitUncommittedChanges.value)
      git.gitDescribedVersion.value.get + "-SNAPSHOT"
    else
      git.gitDescribedVersion.value.get
  },
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
    "-Xmacro-settings:materialize-derivations"
  ),
  resolvers ++= Seq(
    Resolver
      .bintrayRepo("bkirwi", "maven"), // Required for `decline` dependency
    Resolver.bintrayRepo("azavea", "maven"),
    Resolver.bintrayRepo("azavea", "geotrellis"),
    Resolver.sonatypeRepo("releases"),
    Resolver.sonatypeRepo("snapshots"),
    "osgeo-snapshots" at "https://repo.osgeo.org/repository/snapshot/",
    "osgeo-releases" at "https://repo.osgeo.org/repository/release/",
    "eclipse-releases" at "https://repo.eclipse.org/content/groups/releases",
    "eclipse-snapshots" at "https://repo.eclipse.org/content/groups/snapshots"
  ),
  addCompilerPlugin(kindProjector cross CrossVersion.full),
  addCompilerPlugin(macrosParadise cross CrossVersion.full),
  shellPrompt := { s =>
    Project.extract(s).currentProject.id + " > "
  },
  run / fork := true,
  outputStrategy := Some(StdoutOutput),
  assembly / test := {},
  sources in (Compile, doc) := (sources in (Compile, doc)).value,
  assembly / assemblyMergeStrategy := {
    case "reference.conf"   => MergeStrategy.concat
    case "application.conf" => MergeStrategy.concat
    case PathList("META-INF", xs @ _*) =>
      xs match {
        case ("MANIFEST.MF" :: Nil) =>
          MergeStrategy.discard
        case ("services" :: _ :: Nil) =>
          MergeStrategy.concat
        case ("javax.media.jai.registryFile.jai" :: Nil) |
            ("registryFile.jai" :: Nil) | ("registryFile.jaiext" :: Nil) =>
          MergeStrategy.concat
        case (name :: Nil)
            if name.endsWith(".RSA") || name.endsWith(".DSA") || name
              .endsWith(".SF") =>
          MergeStrategy.discard
        case _ =>
          MergeStrategy.first
      }
    case _ => MergeStrategy.first
  },
  headerLicense := Some(
    HeaderLicense.ALv2(java.time.Year.now.getValue.toString, "Azavea")
  ),
  headerMappings := Map(
    FileType.scala -> CommentStyle.cStyleBlockComment.copy(
      commentCreator = { (text, existingText) => {
        // preserve year of old headers
        val newText = CommentStyle.cStyleBlockComment.commentCreator.apply(text, existingText)
        existingText.flatMap(_ => existingText.map(_.trim)).getOrElse(newText)
      } }
    )
  ),
  Global / cancelable := true,
  useCoursier := false,
  javaOptions ++= Seq("-Djava.library.path=/usr/local/lib")
)

lazy val noPublishSettings = Seq(
  publish := {},
  publishLocal := {},
  publishArtifact := false
)

lazy val publishSettings = Seq(
  organization := "com.azavea.geotrellis",
  organizationName := "GeoTrellis",
  organizationHomepage := Some(new URL("https://geotrellis.io/")),
  description := "GeoTrellis Server is a set of components designed to simplify viewing, processing, and serving raster data from arbitrary sources with an emphasis on doing so in a functional style.",
  Test / publishArtifact := false
) ++ sonatypeSettings ++ credentialSettings

lazy val sonatypeSettings = Seq(
  publishMavenStyle := true,
  sonatypeProfileName := "com.azavea",
  sonatypeProjectHosting := Some(
    GitHubHosting(
      user = "geotrellis",
      repository = "maml",
      email = "systems@azavea.com"
    )
  ),
  developers := List(
    Developer(
      id = "moradology",
      name = "Nathan Zimmerman",
      email = "nzimmerman@azavea.com",
      url = url("https://github.com/moradology")
    ),
    Developer(
      id = "echeipesh",
      name = "Eugene Cheipesh",
      email = "echeipesh@azavea.com",
      url = url("https://github.com/echeipesh")
    ),
    Developer(
      id = "pomadchin",
      name = "Grigory Pomadchin",
      email = "gpomadchin@azavea.com",
      url = url("https://github.com/pomadchin")
    )
  ),
  licenses := Seq(
    "Apache-2.0" -> url("https://www.apache.org/licenses/LICENSE-2.0.txt")
  ),
  publishTo := sonatypePublishTo.value
)

lazy val credentialSettings = Seq(
  credentials += Credentials(
    "GnuPG Key ID",
    "gpg",
    System.getenv().get("GPG_KEY_ID"),
    "ignored"
  ),
  credentials += Credentials(
    "Sonatype Nexus Repository Manager",
    "oss.sonatype.org",
    System.getenv().get("SONATYPE_USERNAME"),
    System.getenv().get("SONATYPE_PASSWORD")
  )
)

lazy val root = project
  .in(file("."))
  .settings(moduleName := "root")
  .settings(commonSettings)
  .settings(publishSettings)
  .settings(noPublishSettings)
  .aggregate(core, example, ogc, opengis, `ogc-example`, `stac-example`)

lazy val core = project
  .settings(moduleName := "geotrellis-server-core")
  .settings(commonSettings)
  .settings(publishSettings)
  .settings(
    assembly / assemblyJarName := "geotrellis-server-core.jar",
    libraryDependencies ++= Seq(
      circeCore.value,
      circeGeneric.value,
      circeParser.value,
      circeOptics.value,
      circeShapes.value,
      geotrellisS3,
      geotrellisStore,
      cats.value,
      catsEffect.value,
      mamlJvm,
      simulacrum,
      scalatest,
      droste,
      log4cats
    )
  )

lazy val example = project
  .settings(commonSettings)
  .settings(noPublishSettings)
  .dependsOn(core)
  .settings(
    moduleName := "geotrellis-server-example",
    assembly / assemblyJarName := "geotrellis-server-example.jar",
    libraryDependencies ++= Seq(
      http4sDsl.value,
      http4sBlazeServer.value,
      http4sBlazeClient.value,
      http4sCirce.value,
      http4sXml.value,
      scalaXml,
      geotrellisS3,
      geotrellisStore,
      geotrellisGdal,
      decline,
      commonsIO,
      concHashMap,
      pureConfig,
      pureConfigCatsEffect,
      sttp,
      sttpCats,
      sttpCirce,
      scalatest,
      jaxbApi,
      log4cats
    )
  )
  .settings(
    dependencyOverrides += "com.azavea.gdal" % "gdal-warp-bindings" % "33.60a6918"
  )

lazy val opengis = project
  .enablePlugins(ScalaxbPlugin)
  .settings(moduleName := "geotrellis-server-opengis")
  .settings(commonSettings)
  .settings(publishSettings)
  .settings(
    libraryDependencies ++= Seq(
      scalaXml,
      scalaParser,
      jaxbApi
    )
  )
  .settings(
    Compile / scalaxb / scalaxbDispatchVersion := dispatchVer,
    Compile / scalaxb / scalaxbPackageName := "generated",
    Compile / scalaxb / scalaxbProtocolPackageName := Some("opengis"),
    Compile / scalaxb / scalaxbPackageNames := Map(
      uri("http://www.w3.org/1999/xlink") -> "xlink",
      uri("http://www.opengis.net/wms") -> "opengis.wms",
      uri("http://www.opengis.net/ogc") -> "opengis.ogc",
      uri("http://www.opengis.net/wmts/1.0") -> "opengis.wmts",
      uri("http://www.opengis.net/ows/1.1") -> "opengis.ows",
      uri("http://www.opengis.net/ows") -> "opengis.sld.ows",
      uri("http://www.opengis.net/wcs/1.1.1") -> "opengis.wcs",
      uri("http://www.opengis.net/gml") -> "opengis.gml",
      uri("http://www.opengis.net/filter") -> "opengis.filter",
      uri("http://www.opengis.net/se") -> "opengis.se",
      uri("http://www.opengis.net/sld") -> "opengis.sld",
      uri("http://www.opengis.net/wfs") -> "opengis.wfs",
      uri("http://www.w3.org/2001/SMIL20/") -> "opengis.gml.smil",
      uri("http://www.w3.org/2001/SMIL20/Language") -> "opengis.gml.smil"
    )
  )

lazy val ogc = project
  .dependsOn(core, opengis)
  .settings(moduleName := "geotrellis-server-ogc")
  .settings(commonSettings)
  .settings(publishSettings)
  .settings(
    assembly / assemblyJarName := "geotrellis-server-ogc.jar",
    libraryDependencies ++= Seq(
      geotrellisS3,
      geotrellisStore,
      commonsIo, // to make GeoTiffRasterSources work
      scaffeine,
      scalatest,
      jaxbApi
    )
  )

lazy val `ogc-example` = project
  .dependsOn(ogc)
  .enablePlugins(DockerPlugin)
  .settings(moduleName := "geotrellis-server-ogc-example")
  .settings(commonSettings)
  .settings(noPublishSettings)
  .settings(
    assembly / assemblyJarName := "geotrellis-server-ogc-services.jar",
    libraryDependencies ++= Seq(
      geotrellisS3,
      geotrellisStore,
      geotrellisCassandra,
      geotrellisHBase,
      geotrellisAccumulo,
      geotrellisGdal,
      http4sDsl.value,
      http4sBlazeServer.value,
      http4sBlazeClient.value,
      http4sCirce.value,
      http4sXml.value,
      logback,
      pureConfig,
      pureConfigCatsEffect,
      scaffeine,
      scalatest,
      decline,
      ansiColors212
    ),
    excludeDependencies ++= Seq(
      // log4j brought in via uzaygezen is a pain for us
      ExclusionRule("log4j", "log4j"),
      ExclusionRule("org.slf4j", "slf4j-log4j12"),
      ExclusionRule("org.slf4j", "slf4j-nop")
    )
  )

lazy val `stac-example` = project
  .dependsOn(ogc)
  .settings(moduleName := "geotrellis-stac-example")
  .settings(commonSettings)
  .settings(publishSettings)
  .settings(crossScalaVersions := Seq(scalaVer))
  .settings(
    libraryDependencies ++= Seq(
      geotrellisGdal,
      http4sDsl.value,
      http4sBlazeServer.value,
      http4sBlazeClient.value,
      http4sCirce.value,
      http4sXml.value,
      logback,
      pureConfig,
      pureConfigCatsEffect,
      scaffeine,
      scalatest,
      decline,
      stac4s,
      ansiColors212
    ),
    excludeDependencies ++= Seq(
      // log4j brought in via uzaygezen is a pain for us
      ExclusionRule("log4j", "log4j"),
      ExclusionRule("org.slf4j", "slf4j-log4j12"),
      ExclusionRule("org.slf4j", "slf4j-nop")
    ),
    assembly / assemblyJarName := "geotrellis-stac-example.jar"
  )

lazy val bench = project
  .dependsOn(core)
  .settings(commonSettings)
  .settings(noPublishSettings)
  .enablePlugins(JmhPlugin)
