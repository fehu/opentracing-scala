ThisBuild / scalaVersion     := "2.12.8"
ThisBuild / version          := "0.1.0-SNAPSHOT"
ThisBuild / organization     := "com.github.fehu"

lazy val opentracingVersion = "0.31.0"
lazy val catsVersion = "1.6.0"
lazy val opentracingScalaAkkaVersion = "0.1.0-SNAPSHOT"


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
      "io.opentracing" % "opentracing-util" % opentracingVersion,
      "org.typelevel" %% "cats-core"        % catsVersion,
      "org.scalatest" %% "scalatest"        % "3.0.5"            % Test,
      "io.opentracing" % "opentracing-mock" % opentracingVersion % Test,
      "org.typelevel" %% "cats-effect"      % "1.2.0"            % Test
    ),
    addCompilerPlugin("org.spire-math" %% "kind-projector" % "0.9.9" cross CrossVersion.binary),
  )

lazy val akka = (project in file("akka"))
  .settings(
    name := "opentracing-akka",
    libraryDependencies += "com.github.fehu" %% "opentracing-contrib-scala-akka-fork" % opentracingScalaAkkaVersion
  )
  .dependsOn(scala)

// See https://www.scala-sbt.org/1.x/docs/Using-Sonatype.html for instructions on how to publish to Sonatype.
