// scalac plugin has its own version

val scala212 = "2.12.11"
val scala213 = "2.13.1"

ThisBuild / crossScalaVersions := List(scala212, scala213)
ThisBuild / scalaVersion     := scala213
ThisBuild / version          := "0.1.8.5"
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
  .aggregate(scala, akka, effect, akkaEffect, fs2)

lazy val scala = (project in file("scala"))
  .settings(
    name := "opentracing-scala",
    libraryDependencies ++= Seq(
      Dependencies.`opentracing-api`,
      Dependencies.`cats-core`,
      Dependencies.`cats-effect` % Test
    ),
    libraryDependencies ++= testDependencies
  )

lazy val akka = (project in file("akka"))
  .settings(
    name := "opentracing-akka",
    libraryDependencies += Dependencies.`akka-actor`,
    libraryDependencies ++= testDependencies
  )
  .dependsOn(scala % "compile->compile;test->test")

lazy val effect = (project in file("effect"))
  .settings(
    name := "opentracing-effect",
    libraryDependencies += Dependencies.`cats-effect`
  )
  .dependsOn(scala)

lazy val akkaEffect = (project in file("akka-effect"))
  .settings(
    name := "opentracing-akka-effect"
  )
  .dependsOn(akka, effect)

lazy val fs2 = (project in file("fs2"))
  .settings(
    name := "opentracing-fs2",
    libraryDependencies += Dependencies.`fs2-core`
  )
  .dependsOn(effect)


lazy val testDependencies = Seq(
  Dependencies.scalatest          % Test,
  Dependencies.`opentracing-mock` % Test
)


// Has its own configuration file (and own version)
lazy val compilerPlugin = project in file("compiler-plugin")
