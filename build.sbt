import xerial.sbt.Sonatype._

import Dependencies._

scalaVersion := scalaVer
scalaVersion in ThisBuild := scalaVer
updateOptions := updateOptions.value.withCachedResolution(true)

lazy val commonSettings = Seq(
  // We are overriding the default behavior of sbt-git which, by default,
  // only appends the `-SNAPSHOT` suffix if there are uncommitted
  // changes in the workspace.
  version := {
    // Avoid Cyclic reference involving error
    if (git.gitCurrentTags.value.isEmpty || git.gitUncommittedChanges.value)
      git.gitDescribedVersion.value.get + "-SNAPSHOT"
    else
      git.gitDescribedVersion.value.get
  },
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
    "-Ypatmat-exhaust-depth",
    "100",
    "-Xmacro-settings:materialize-derivations"
  ),
  resolvers ++= Seq(
    Resolver
      .bintrayRepo("bkirwi", "maven"), // Required for `decline` dependency
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
  shellPrompt := { s =>
    Project.extract(s).currentProject.id + " > "
  },
  fork in run := true,
  outputStrategy := Some(StdoutOutput),
  test in assembly := {},
  sources in (Compile, doc) := (sources in (Compile, doc)).value,
  assemblyMergeStrategy in assembly := {
    case "reference.conf"   => MergeStrategy.concat
    case "application.conf" => MergeStrategy.concat
    case n if n.endsWith(".SF") || n.endsWith(".RSA") || n.endsWith(".DSA") =>
      MergeStrategy.discard
    case "META-INF/MANIFEST.MF" => MergeStrategy.discard
    case _                      => MergeStrategy.first
  },
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
  publishArtifact in Test := false
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
    "Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0.txt")
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
  .aggregate(core, example, ogc, ogcExample, opengis, stac)

lazy val core = project
  .settings(moduleName := "geotrellis-server-core")
  .settings(commonSettings)
  .settings(publishSettings)
  .settings(
    assemblyJarName in assembly := "geotrellis-server-core.jar",
    libraryDependencies ++= Seq(
      circeCore.value,
      circeGeneric.value,
      circeParser.value,
      circeOptics.value,
      circeShapes.value,
      geotrellisS3,
      geotrellisSpark,
      spark,
      cats.value,
      catsEffect.value,
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
      http4sDsl.value,
      http4sBlazeServer.value,
      http4sBlazeClient.value,
      http4sCirce.value,
      http4sXml.value,
      scalaXml,
      geotrellisS3,
      geotrellisSpark,
      geotrellisGdal,
      spark,
      decline,
      commonsIO,
      concHashMap,
      pureConfig,
      typesafeLogging,
      sttp,
      sttpCats,
      sttpCirce,
      scalatest,
      jaxbApi
    )
  )
  .settings(
    dependencyOverrides += "com.azavea.gdal" % "gdal-warp-bindings" % "33.5523882"
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
    scalaxbDispatchVersion in (Compile, scalaxb) := dispatchVer,
    scalaxbPackageName in (Compile, scalaxb) := "generated",
    scalaxbProtocolPackageName in scalaxb in Compile := Some("opengis"),
    scalaxbPackageNames in scalaxb in Compile := Map(
      uri("http://www.w3.org/1999/xlink") -> "xlink",
      uri("http://www.opengis.net/wms") -> "opengis.wms",
      uri("http://www.opengis.net/ogc") -> "opengis.ogc",
      uri("http://www.opengis.net/wmts/1.0") -> "opengis.wmts",
      uri("http://www.opengis.net/ows/1.1") -> "opengis.ows",
      uri("http://www.opengis.net/ows/1.0") -> "opengis.sld.ows",
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
    assemblyJarName in assembly := "geotrellis-server-ogc.jar",
    libraryDependencies ++= Seq(
      spark,
      geotrellisS3,
      geotrellisSpark,
      typesafeLogging,
      commonsIo, // to make GeoTiffRasterSources work
      slf4jApi, // enable logging
      scaffeine,
      scalatest,
      jaxbApi
    )
  )

lazy val ogcExample = (project in file("ogc-example"))
  .dependsOn(ogc)
  .enablePlugins(DockerPlugin)
  .settings(moduleName := "geotrellis-server-ogc-example")
  .settings(commonSettings)
  .settings(noPublishSettings)
  .settings(
    assemblyJarName in assembly := "geotrellis-server-ogc-services.jar",
    libraryDependencies ++= Seq(
      spark,
      geotrellisS3,
      geotrellisSpark,
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
    libraryDependencies := (CrossVersion
      .partialVersion(scalaVersion.value) match {
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
      cats.value,
      circeCore.value,
      circeGeneric.value,
      circeParser.value,
      circeRefined.value,
      circeShapes.value,
      geotrellisS3,
      refined,
      shapeless,
      scalacheck,
      scalacheckCats,
      scalatest,
      spdxChecker
    )
  )

lazy val bench = project
  .dependsOn(core)
  .settings(noPublishSettings)
  .enablePlugins(JmhPlugin)
