import sbt._
import sbt.Keys._

object Dependencies {

  lazy val `scala-compiler` = Def.setting{ scalaOrganization.value % "scala-compiler" % scalaVersion.value }

  lazy val `opentracing-api`  = "io.opentracing"   % "opentracing-api"  % Version.opentracing
  lazy val `opentracing-mock` = "io.opentracing"   % "opentracing-mock" % Version.opentracing
  lazy val `jaeger-client`    = "io.jaegertracing" % "jaeger-client"    % Version.jaegerClient

  lazy val `cats-core`        = "org.typelevel"  %% "cats-core"        % Version.cats
  lazy val `cats-effect`      = "org.typelevel"  %% "cats-effect"      % Version.catsEffect
  lazy val `kind-projector`   = "org.typelevel"  %% "kind-projector"   % Version.kindProjector

  lazy val `akka-actor` = "com.typesafe.akka" %% "akka-actor" % Version.akka

  lazy val scalatest    = "org.scalatest"     %% "scalatest"  % Version.scalatest

  object Version {
    lazy val opentracing    = "0.33.0"
    lazy val jaegerClient   = "0.33.1"

    lazy val cats           = "2.1.1"
    lazy val catsEffect     = "2.1.3"
    lazy val kindProjector  = "0.11.0"

    lazy val akka           = "2.6.4"

    lazy val scalatest      = "3.1.1"
  }
}
