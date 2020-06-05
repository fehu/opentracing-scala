// scalac plugin has its own version

val scala213 = "2.13.2"

ThisBuild / crossScalaVersions := List(scala213)
ThisBuild / scalaVersion     := scala213
ThisBuild / version          := "0.2.0"
ThisBuild / organization     := "com.arkondata"


ThisBuild / homepage   := Some(url("https://github.com/Grupo-Abraxas/opentracing-scala"))
ThisBuild / scmInfo    := Some(ScmInfo(homepage.value.get, "git@github.com:Grupo-Abraxas/opentracing-scala.git"))
ThisBuild / developers := List(
                            Developer("fehu", "Dmitry K", "kdn.kovalev@gmail.com", url("https://github.com/fehu"))
                          )
ThisBuild / licenses += ("MIT", url("https://opensource.org/licenses/MIT"))


inThisBuild(Seq(
  scalacOptions in Compile ++= Seq("-feature", "-deprecation", "-unchecked"),
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

// Tests

Global / concurrentRestrictions := Seq(
  Tags.limit(Tags.CPU, 1),
  Tags.limit(Tags.Test, 1)
)

// Publishing

ThisBuild / publishMavenStyle := true

ThisBuild / publishTo := Some(
  if (isSnapshot.value)
    Opts.resolver.sonatypeSnapshots
  else
    Opts.resolver.sonatypeStaging
)

ThisBuild / credentials += Credentials(
  "Sonatype Nexus Repository Manager",
  "oss.sonatype.org",
  sys.env.getOrElse("SONATYPE_USER", ""),
  sys.env.getOrElse("SONATYPE_PWD", "")
)

// Fix for error `java.net.ProtocolException: Too many follow-up requests: 21`
// See [[https://github.com/sbt/sbt-pgp/issues/150]]
ThisBuild / updateOptions := updateOptions.value.withGigahorse(false)
