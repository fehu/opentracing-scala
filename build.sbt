// scalac plugin has its own version

ThisBuild / scalaVersion     := "2.12.8"
ThisBuild / version          := "0.1.6"
ThisBuild / organization     := "com.github.fehu"

lazy val root = (project in file("."))
  .settings(
    name := "opentracing",
    publishArtifact := false
  )
  .aggregate(scala, akka)

lazy val scala = (project in file("scala"))
  .settings(
    name := "opentracing-scala",
    libraryDependencies ++= Seq(
      Dependencies.`opentracing-api`,
      Dependencies.`cats-core`,
      Dependencies.`cats-effect` % Test
    ),
    libraryDependencies ++= testDependencies,
    addCompilerPlugin(Dependencies.`kind-projector` cross CrossVersion.binary),
  )

lazy val akka = (project in file("akka"))
  .settings(
    name := "opentracing-akka",
    libraryDependencies += Dependencies.`akka-actor`,
    libraryDependencies ++= testDependencies,
    addCompilerPlugin(Dependencies.`kind-projector` cross CrossVersion.binary)
  )
  .dependsOn(scala % "compile->compile;test->test")


lazy val testDependencies = Seq(
  Dependencies.scalatest          % Test,
  Dependencies.`opentracing-mock` % Test
)


// Has its own configuration file (and own version)
lazy val compilerPlugin = project in file("compiler-plugin")
