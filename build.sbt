// scalac plugin has its own version

val scala213 = "2.13.2"

ThisBuild / crossScalaVersions := List(scala213)
ThisBuild / scalaVersion     := scala213
ThisBuild / version          := "0.3.0-SNAPSHOT"
ThisBuild / organization     := "com.github.fehu"

inThisBuild(Seq(
  addCompilerPlugin(Dependencies.`kind-projector`),
  addCompilerPlugin(Dependencies.`monadic-for`)
))

lazy val root = (project in file("."))
  .settings(
    name := "opentracing",
    publishArtifact := false
  )
  .aggregate(scalaNew, fs2New)

lazy val scalaNew = (project in file("scala-new"))
  .settings(
    name := "opentracing-scala-new",
    libraryDependencies ++= Seq(
      Dependencies.`opentracing-api`,
      Dependencies.`cats-core`,
      Dependencies.`cats-effect`
    ),
    libraryDependencies ++= testDependencies
  )

lazy val fs2New = (project in file("fs2-new"))
  .settings(
    name := "opentracing-fs2-new",
    libraryDependencies += Dependencies.`fs2-core`
  )
  .dependsOn(scalaNew)

lazy val testDependencies = Seq(
  Dependencies.scalatest          % Test,
  Dependencies.`opentracing-mock` % Test
)


// Has its own configuration file (and own version)
lazy val compilerPlugin = project in file("compiler-plugin")
