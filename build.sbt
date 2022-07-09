// scalac plugin has its own version

val scala212 = "2.12.16"
val scala213 = "2.13.8"
val scala3   = "3.1.3"

ThisBuild / crossScalaVersions := List(scala212, scala213, scala3)
ThisBuild / scalaVersion       := scala213
ThisBuild / version            := "0.7.0-SNAPSHOT"
ThisBuild / organization       := "io.github.fehu"
ThisBuild / versionScheme      := Some("semver-spec")

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
      "-Yexplicit-nulls",
      "-Ysafe-init",
      "-source:future",
    )
    case Some((2, 12 | 13)) => Seq(
      "-language:higherKinds",
      "-Xsource:3",
      "-P:kind-projector:underscore-placeholders"
    )
  }
}

// // // Modules // // //

def moduleName(suff: String): String = {
  val suff1 = if (suff.nonEmpty) s"-$suff" else suff
  s"opentracing-scala$suff1"
}

lazy val root = (project in file("."))
  .settings(
    name := moduleName(""),
    publish / skip := true
  )
  .aggregate(core, akka, fs2, noop, jaeger)

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

// // // Backends // // //

lazy val noop = (project in file("noop"))
  .settings(
    name := moduleName("noop"),
    libraryDependencies += Dependencies.`opentracing-noop`
  ).dependsOn(core)

lazy val jaeger = (project in file("jaeger"))
  .settings(
    name := moduleName("jaeger"),
    libraryDependencies += Dependencies.`jaeger-client`
  ).dependsOn(core)

// // // Tests // // //

lazy val testDependencies = Seq(
  Dependencies.scalatest          % Test,
  Dependencies.`opentracing-mock` % Test
)

ThisBuild / Test / parallelExecution := false

// // // Compiler Plugin (Scala 2 only) // // //

// Has its own configuration file (and own version)
lazy val `compiler-plugin` = project in file("compiler-plugin") settings (
  crossScalaVersions := List(scala212, scala213),
  crossVersion := CrossVersion.full
)

// // // Misc // // //

addCommandAlias("fullDependencyUpdates", ";dependencyUpdates; reload plugins; dependencyUpdates; reload return")


// // // Publishing // // //

ThisBuild / crossVersion := CrossVersion.binary
ThisBuild / publishTo    := sonatypePublishToBundle.value

// Continues at [[sonatype.sbt]]
