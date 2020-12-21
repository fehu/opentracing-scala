import sbt._
import sbt.Keys._

object Dependencies {

  lazy val `scala-compiler` = Def.setting{ scalaOrganization.value % "scala-compiler" % scalaVersion.value }

  lazy val `opentracing-api`  = "io.opentracing"   % "opentracing-api"  % Version.opentracing
  lazy val `opentracing-mock` = "io.opentracing"   % "opentracing-mock" % Version.opentracing
  lazy val `jaeger-client`    = "io.jaegertracing" % "jaeger-client"    % Version.jaegerClient

  lazy val `cats-core`        = "org.typelevel"  %% "cats-core"        % Version.cats
  lazy val `cats-effect`      = "org.typelevel"  %% "cats-effect"      % Version.catsEffect
  lazy val `fs2-core`         = "co.fs2"         %% "fs2-core"         % Version.fs2

  lazy val `kind-projector`   = "org.typelevel"  %% "kind-projector"     % Version.kindProjector cross CrossVersion.full
  lazy val `monadic-for`      = "com.olegpy"     %% "better-monadic-for" % Version.monadicFor

  lazy val `akka-actor` = "com.typesafe.akka" %% "akka-actor" % Version.akka

  lazy val scalatest    = "org.scalatest"     %% "scalatest"  % Version.scalatest

  object Version {
    lazy val opentracing    = "0.33.0"
    lazy val jaegerClient   = "0.33.1"

    lazy val cats           = "2.3.1"
    lazy val catsEffect     = "2.3.1"
    lazy val fs2            = "2.4.6"

    lazy val kindProjector  = "0.11.2"
    lazy val monadicFor     = "0.3.1"

    lazy val akka           = "2.6.10"

    lazy val scalatest      = "3.2.3"
  }
}
