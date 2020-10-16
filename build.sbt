// scalac plugin has its own version

val scala213 = "2.13.2"

ThisBuild / crossScalaVersions := List(scala213)
ThisBuild / scalaVersion     := scala213
ThisBuild / version          := "0.3.0-RC2"
ThisBuild / organization     := "com.github.fehu"

inThisBuild(Seq(
  addCompilerPlugin(Dependencies.`kind-projector`),
  addCompilerPlugin(Dependencies.`monadic-for`)
))

lazy val root = (project in file("."))
  .settings(
    name := "opentracing",
    skip in publish := true
  )
  .aggregate(scala, akka, fs2)

lazy val scala = (project in file("scala"))
  .settings(
    name := "opentracing-scala",
    libraryDependencies ++= Seq(
      Dependencies.`opentracing-api`,
      Dependencies.`cats-core`,
      Dependencies.`cats-effect`
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

lazy val fs2 = (project in file("fs2"))
  .settings(
    name := "opentracing-fs2",
    libraryDependencies += Dependencies.`fs2-core`
  )
  .dependsOn(scala)

lazy val testDependencies = Seq(
  Dependencies.scalatest          % Test,
  Dependencies.`opentracing-mock` % Test
)


// Has its own configuration file (and own version)
lazy val compilerPlugin = project in file("compiler-plugin")
