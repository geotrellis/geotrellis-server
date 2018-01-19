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
  commonsIO,
  ficus,
  geotrellisS3,
  geotrellisSpark,
  scalatest,
  spark
)
