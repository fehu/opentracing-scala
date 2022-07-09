//> using scala "3.1.3"

import scala.util.chaining.*
import scala.sys.process.Process

import ReleaseDefs.*

@main
def check(module: String): Unit = runSbt(module, Stage.Check, "release with-defaults")

@main
def test(module: String): Unit = runSbt(module, Stage.Test, "release with-defaults")

@main
def preRelease(module: String, version0: String): Unit =
  val version = nonEmpty(version0, "release-version")
  runSbt(module, Stage.PreRelease, s"release$version with-defaults")

@main
def release(module: String, target0: String): Unit =
  val target = s"set Release.releaseTarget := ReleaseDefs.Target.${Target.parse(target0)}"
  runSbt(module, Stage.Release, target, "release with-defaults")

@main
def postRelease(module: String, nextVersion0: String): Unit =
  val nextVersion = nonEmpty(nextVersion0, "next-version")
  runSbt(module, Stage.PostRelease, s"release$nextVersion with-defaults")

@main
def push(module: String): Unit = runSbt(module, Stage.Push, "release with-defaults")

@main
def step(step0: String, module: String, version: String, nextVersion: String, target0: String): Unit =
  Stage.parse(step0) match {
    case Stage.Check       => check(module)
    case Stage.Test        => test(module)
    case Stage.PreRelease  => preRelease(module, version)
    case Stage.Release     => release(module, target0)
    case Stage.PostRelease => postRelease(module, nextVersion)
    case Stage.Push        => push(module)
  }

inline private def nonEmpty(s0: String, cmd: String) =
  s0.trim.pipe(s => if s.isEmpty || s == "<none>" then "" else s" $cmd $s")


inline private def runSbt(module: String, stage: Stage, cmds: String*): Unit =
  val setModule = nonEmpty(module, ";project")
  val setStage = s"set Release.releaseStage := ReleaseDefs.Stage.$stage"
  val cmd = (setModule +: setStage +: cmds).mkString(" ;")

  println(s"Running sbt $"$cmd$"")
  val result = Process("sbt", Seq(cmd)).!
  sys.exit(result)
