// scalac plugin has its own version

val scala213 = "2.13.14"
val scala3   = "3.1.3"

ThisBuild / crossScalaVersions := List(scala213, scala3)
ThisBuild / scalaVersion       := scala213
ThisBuild / organization       := "io.github.fehu"
ThisBuild / versionScheme      := Some("semver-spec")

ThisBuild / libraryDependencies ++= {
  (CrossVersion.partialVersion(scalaVersion.value): @unchecked) match {
    case Some((2, 13)) =>
      Seq(
        Dependencies.`kind-projector`,
        Dependencies.`monadic-for`
      ).map(sbt.compilerPlugin)
    case Some((3, _)) =>
      Seq()
  }
}

ThisBuild / Compile / scalacOptions ++= Seq(
  "-feature",
  "-deprecation"
) ++ {
  (CrossVersion.partialVersion(scalaVersion.value): @unchecked) match {
    case Some((3, _)) =>
      Seq(
        "-Ykind-projector:underscores",
        "-Yexplicit-nulls",
        "-Ysafe-init",
        "-source:future"
      )
    case Some((2, 13)) =>
      Seq(
        "-language:higherKinds",
        "-Xsource:3",
        "-P:kind-projector:underscore-placeholders"
      )
  }
}

// // // Modules // // //

val namePrefix = "opentracing-scala"

def moduleName(suff: String): String = s"$namePrefix-$suff"

def module(nme: String): Project =
  Project(nme, file(nme)).settings(
    name := moduleName(nme),
    releaseModuleSettings
  )

lazy val root = (project in file("."))
  .settings(
    name := namePrefix,
    releaseCommonSettings,
    publish / skip          := true,
    sonatypePublishTo       := None,
    sonatypePublishToBundle := None
  )
  .aggregate(core, akka, fs2, noop, jaeger)

lazy val core = module("core")
  .settings(
    libraryDependencies ++= Seq(
      Dependencies.`opentracing-api`,
      Dependencies.`cats-core`,
      Dependencies.`cats-effect`
    ),
    libraryDependencies ++= testDependencies
  )

lazy val akka = module("akka")
  .settings(
    libraryDependencies += Dependencies.`akka-actor`,
    libraryDependencies ++= testDependencies
  )
  .dependsOn(core % "compile->compile;test->test")

lazy val fs2 = module("fs2")
  .settings(
    libraryDependencies += Dependencies.`fs2-core`
  )
  .dependsOn(core)

// // // Backends // // //

lazy val noop = module("noop")
  .settings(
    libraryDependencies += Dependencies.`opentracing-noop`
  )
  .dependsOn(core)

lazy val jaeger = module("jaeger")
  .settings(
    libraryDependencies += Dependencies.`jaeger-client`
  )
  .dependsOn(core)

// // // Tests // // //

lazy val testDependencies = Seq(
  Dependencies.scalatest          % Test,
  Dependencies.`opentracing-mock` % Test
)

ThisBuild / Test / parallelExecution := false

// // // Compiler Plugin (Scala 2 only) // // //

// Has its own configuration file (and own version)
lazy val `compiler-plugin` = project in file("compiler-plugin") settings (
  releaseCommonSettings,
  crossScalaVersions := List(scala213)
)

// // // Misc // // //

addCommandAlias("fullDependencyUpdates", ";dependencyUpdates; reload plugins; dependencyUpdates; reload return")

// // // Publishing // // //

ThisBuild / crossVersion := CrossVersion.binary

// Continues at [[sonatype.sbt]]

// // // Release // // //

import Release._
import ReleaseDefs._

ThisBuild / releaseVerFile := (root / releaseVersionFile).value
ThisBuild / releaseOutDir  := (root / sonatypeBundleDirectory).value

lazy val releaseCommonSettings: Def.SettingsDefinition = Seq(
  publishTo                     := sonatypePublishToBundle.value,
  releaseCrossBuild             := true,
  releaseProcess                := stages.value(releaseStage.value),
  releaseTarget                 := Target.Staging,
  releaseStage                  := Stage.Check,
  releasePublishArtifactsAction := PgpKeys.publishSigned.value
)

lazy val releaseModuleSettings: Def.SettingsDefinition = releaseCommonSettings.settings ++ Seq(
  releaseVersionFile      := releaseVerFile.value,
  sonatypeBundleDirectory := releaseOutDir.value
)
