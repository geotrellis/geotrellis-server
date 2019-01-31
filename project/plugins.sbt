addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "0.14.6")

addSbtPlugin("net.virtual-void" % "sbt-dependency-graph" % "0.9.0")

addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full)

addSbtPlugin("pl.project13.scala" % "sbt-jmh" % "0.3.4")

addCompilerPlugin("org.spire-math" %% "kind-projector" % "0.9.7")

addSbtPlugin("com.eed3si9n" % "sbt-unidoc" % "0.4.1")

addSbtPlugin("com.47deg" % "sbt-microsites" % "0.7.23")

addSbtPlugin("org.foundweekends" % "sbt-bintray" % "0.5.4")

addSbtPlugin("com.github.gseitz" % "sbt-release" % "1.0.10")
