import sbt._

object Dependencies {

  lazy val `opentracing-api`  = "io.opentracing"  % "opentracing-api"  % Version.opentracing
  lazy val `opentracing-mock` = "io.opentracing"  % "opentracing-mock" % Version.opentracing

  lazy val `cats-core`        = "org.typelevel"  %% "cats-core"        % Version.cats
  lazy val `cats-effect`      = "org.typelevel"  %% "cats-effect"      % Version.catsEffect
  lazy val `kind-projector`   = "org.spire-math" %% "kind-projector"   % Version.kindProjector

  lazy val `akka-actor` = "com.typesafe.akka" %% "akka-actor" % Version.akka

  lazy val scalatest    = "org.scalatest"     %% "scalatest"  % Version.scalatest

  object Version {
    lazy val opentracing    = "0.31.0"

    lazy val cats           = "1.6.0"
    lazy val catsEffect     = "1.2.0"
    lazy val kindProjector  = "0.9.9"

    lazy val akka           = "2.5.16"

    lazy val scalatest      = "3.0.5"
  }
}
