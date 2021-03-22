dockerfile in docker := {
  // The assembly task generates a fat JAR file
  val artifact: File     = assembly.value
  val artifactTargetPath = s"/app/${artifact.name}"

  new Dockerfile {
    from("openjdk:8-jre")
    add(artifact, artifactTargetPath)
    entryPoint("java", "-jar", artifactTargetPath)
  }
}

imageNames in docker := Seq(
  // Sets the latest tag
  ImageName(s"geotrellis/geotrellis-server-ogc-services:latest"),
  // Sets a name with a tag that contains the project version
  ImageName(
    namespace = Some("geotrellis"),
    repository = "geotrellis-server-ogc-services",
    tag = Some("v" + version.value)
  )
)
