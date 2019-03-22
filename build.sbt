ThisBuild / scalaVersion     := "2.12.8"
ThisBuild / version          := "0.1.0-SNAPSHOT"
ThisBuild / organization     := "com.github.fehu"

lazy val opentracingVersion = "0.31.0"
lazy val catsVersion = "1.6.0"
lazy val akkaVersion = "2.5.16"


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
      "io.opentracing" % "opentracing-api"  % opentracingVersion,
      "org.typelevel" %% "cats-core"        % catsVersion,
      "org.typelevel" %% "cats-effect"      % "1.2.0"            % Test
    ),
    libraryDependencies ++= testDependencies,
    addCompilerPlugin("org.spire-math" %% "kind-projector" % "0.9.9" cross CrossVersion.binary),
  )

lazy val akka = (project in file("akka"))
  .settings(
    name := "opentracing-akka",
    libraryDependencies += "com.typesafe.akka" %% "akka-actor" % akkaVersion,
    libraryDependencies ++= testDependencies,
    addCompilerPlugin("org.spire-math" %% "kind-projector" % "0.9.9" cross CrossVersion.binary)
  )
  .dependsOn(scala % "compile->compile;test->test")


lazy val testDependencies = Seq(
  "org.scalatest" %% "scalatest"        % "3.0.5"            % Test,
  "io.opentracing" % "opentracing-mock" % opentracingVersion % Test
)

