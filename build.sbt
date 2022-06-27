// scalac plugin has its own version

val scala212 = "2.12.16"
val scala213 = "2.13.8"
val scala3   = "3.1.3"

ThisBuild / crossScalaVersions := List(scala212, scala213, scala3)
ThisBuild / scalaVersion       := scala213
ThisBuild / version            := "0.7.0-SNAPSHOT"
ThisBuild / organization       := "com.github.fehu"

ThisBuild / libraryDependencies ++= {
  (CrossVersion.partialVersion(scalaVersion.value): @unchecked) match {
    case Some((2, 12 | 13)) =>
      Seq(
        Dependencies.`kind-projector`,
        Dependencies.`monadic-for`,
      ).map(sbt.compilerPlugin)
    case Some((3, _)) =>
      Seq()
  }
}

ThisBuild / Compile / scalacOptions ++= Seq(
  "-feature",
  "-deprecation",
) ++ {
  (CrossVersion.partialVersion(scalaVersion.value): @unchecked) match {
    case Some((3, _)) => Seq(
      "-Ykind-projector:underscores",
      "-source:future",
    )
    case Some((2, 12 | 13)) => Seq(
      "-language:higherKinds",
      "-Xsource:3",
      "-P:kind-projector:underscore-placeholders"
    )
  }
}

ThisBuild / Test / parallelExecution := false

def moduleName(suff: String): String = {
  val suff1 = if (suff.nonEmpty) s"-$suff" else suff
  s"opentracing-scala$suff1"
}

lazy val root = (project in file("."))
  .settings(
    name := moduleName(""),
    publish / skip := true
  )
  .aggregate(core, akka, fs2)

lazy val core = (project in file("core"))
  .settings(
    name := moduleName("core"),
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
    name := moduleName("akka"),
    libraryDependencies += Dependencies.`akka-actor`,
    libraryDependencies ++= testDependencies
  )
  .dependsOn(core % "compile->compile;test->test")

lazy val fs2 = (project in file("fs2"))
  .settings(
    name := moduleName("fs2"),
    libraryDependencies += Dependencies.`fs2-core`
  )
  .dependsOn(core)

lazy val testDependencies = Seq(
  Dependencies.scalatest          % Test,
  Dependencies.`opentracing-mock` % Test
)


// Has its own configuration file (and own version)
lazy val compilerPlugin = project in file("compiler-plugin") settings (
  crossScalaVersions := List(scala212, scala213)
)

addCommandAlias("fullDependencyUpdates", ";dependencyUpdates; reload plugins; dependencyUpdates; reload return")
