import Dependencies._

name := "geotrellis-server-core"
libraryDependencies ++= Seq(
  geotrellisSpark,
  spark,
  scalatest
)
