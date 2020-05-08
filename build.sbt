// scalac plugin has its own version

val scala212 = "2.12.11"
val scala213 = "2.13.1"
val crossScala = List(scala212, scala213)

ThisBuild / scalaVersion     := scala213
ThisBuild / version          := "0.1.8.2"
ThisBuild / organization     := "com.github.fehu"

lazy val root = (project in file("."))
  .settings(
    name := "opentracing",
    publishArtifact := false
  )
  .aggregate(scala, akka, effect)

lazy val scala = (project in file("scala"))
  .settings(
    name := "opentracing-scala",
    libraryDependencies ++= Seq(
      Dependencies.`opentracing-api`,
      Dependencies.`cats-core`,
      Dependencies.`cats-effect` % Test
    ),
    libraryDependencies ++= testDependencies,
    crossScalaVersions := crossScala,
    addCompilerPlugin(Dependencies.`kind-projector` cross CrossVersion.full)
  )

lazy val akka = (project in file("akka"))
  .settings(
    name := "opentracing-akka",
    libraryDependencies += Dependencies.`akka-actor`,
    libraryDependencies ++= testDependencies,
    crossScalaVersions := crossScala,
    addCompilerPlugin(Dependencies.`kind-projector` cross CrossVersion.full)
  )
  .dependsOn(scala % "compile->compile;test->test")

lazy val effect = (project in file("effect"))
  .settings(
    name := "opentracing-effect",
    libraryDependencies += Dependencies.`cats-effect`,
    libraryDependencies ++= testDependencies,
    crossScalaVersions := crossScala,
    addCompilerPlugin(Dependencies.`kind-projector` cross CrossVersion.full)
  )
  .dependsOn(scala % "compile->compile;test->test")


lazy val testDependencies = Seq(
  Dependencies.scalatest          % Test,
  Dependencies.`opentracing-mock` % Test
)


// Has its own configuration file (and own version)
lazy val compilerPlugin = project in file("compiler-plugin") settings (
  crossScalaVersions := crossScala
)
