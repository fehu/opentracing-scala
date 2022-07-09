//> using scala "3.1.3"

import scala.util.chaining.*
import scala.sys.process.Process

@main
def check(module: String): Unit = runSbt(module, "check", "release with-defaults")

@main
def test(module: String): Unit = runSbt(module, "test", "release with-defaults")

@main
def preRelease(module: String, version0: String): Unit =
  val version = nonEmpty(version0, "release-version")
  runSbt(module, "pre-release", s"release$version with-defaults")

@main
def release(module: String, promote0: Boolean): Unit =
  val promote = s"set releasePromote := $promote0"
  runSbt(module, "release", promote, "release with-defaults")

@main
def postRelease(module: String, nextVersion0: String): Unit =
  val nextVersion = nonEmpty(nextVersion0, "next-version")
  runSbt(module, "post-release", s"release$nextVersion with-defaults")

@main
def push(module: String): Unit = runSbt(module, "push", "release with-defaults")

@main
def step(step: String, module: String, version: String, nextVersion: String, promote: Boolean): Unit =
  step match
    case "check"        => check(module)
    case "test"         => test(module)
    case "pre-release"  => preRelease(module, version)
    case "release"      => release(module, promote)
    case "post-release" => postRelease(module, nextVersion)
    case "push"         => push(module)
    case other          => sys.error(s"Unknown step: $"$other$"")


inline private def nonEmpty(s0: String, cmd: String) =
  s0.trim.pipe(s => if s.isEmpty || s == "<none>" then "" else s" $cmd $s")


inline private def runSbt(module: String, stage: String, cmds: String*): Unit =
  val setModule = nonEmpty(module, ";project")
  val setStage = s"set releaseStage := $"$stage$""
  val cmd = (setModule +: setStage +: cmds).mkString(" ;")

  println(s"Running sbt $"$cmd$"")
  val result = Process("sbt", Seq(cmd)).!
  sys.exit(result)
