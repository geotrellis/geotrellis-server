import Dependencies._

name := "geotrellis-server"
libraryDependencies ++= Seq(
  akkaHttp,
  akkaHttpXml,
  akkaCirceJson,
  cats,
  circeCore,
  circeGeneric,
  circeParser,
  ficus,
  scalatest
)
