// scalac plugin has its own version

val scala212 = "2.12.13"
val scala213 = "2.13.5"

ThisBuild / crossScalaVersions := List(scala212, scala213)
ThisBuild / scalaVersion       := scala213
ThisBuild / version            := "0.4.1-SNAPSHOT"
ThisBuild / organization       := "com.github.fehu"

inThisBuild(Seq(
  addCompilerPlugin(Dependencies.`kind-projector`),
  addCompilerPlugin(Dependencies.`monadic-for`),
  Compile / scalacOptions ++= Seq("-feature", "-deprecation"),
  Test / parallelExecution := false
))

lazy val root = (project in file("."))
  .settings(
    name := "opentracing",
    skip in publish := true
  )
  .aggregate(scala, akka, fs2, monix)

lazy val scala = (project in file("scala"))
  .settings(
    name := "opentracing-scala",
    libraryDependencies ++= Seq(
      Dependencies.`opentracing-api`,
      Dependencies.`cats-core`,
      Dependencies.`cats-effect`
    ),
    libraryDependencies ++= testDependencies,
    Compile / scalacOptions ++= {
      CrossVersion.partialVersion(scalaVersion.value) match {
        case Some((2, 12)) => List("-Ypartial-unification")
        case _             => Nil
      }
    }
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

lazy val monix = (project in file("monix"))
  .settings(
    name := "opentracing-monix",
    libraryDependencies += Dependencies.monix
  )
  .dependsOn(scala % "compile->compile;test->test")


lazy val testDependencies = Seq(
  Dependencies.scalatest          % Test,
  Dependencies.`opentracing-mock` % Test
)


// Has its own configuration file (and own version)
lazy val compilerPlugin = project in file("compiler-plugin") settings (
  crossScalaVersions := List(scala212, scala213)
)

addCommandAlias("fullDependencyUpdates", ";dependencyUpdates; reload plugins; dependencyUpdates; reload return")
