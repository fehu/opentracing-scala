import sbt.{Def, File}
import sbtrelease.ReleasePlugin.autoImport._
import sbtrelease.ReleaseStateTransformations._

import ReleaseDefs._

object Release {
  lazy val releaseTarget  = Def.settingKey[Target]("Where to release")
  lazy val releaseStage   = Def.settingKey[Stage]("Release stage")
  lazy val releaseVerFile = Def.settingKey[File]("Common version file")

  lazy val stages: Def.Initialize[Map[Stage, Seq[ReleaseStep]]] = Def.setting(Map(
    Stage.Check -> Seq(
      checkSnapshotDependencies,
      inquireVersions
    ),
    Stage.Test -> Seq(
      runClean,
      runTest
    ),
    Stage.PreRelease -> Seq(
      inquireVersions,
      setReleaseVersion,
      commitReleaseVersion,
      tagRelease
    ),
    Stage.Release -> (releaseTarget.value match {
      case Target.Staging   => Seq("+publishSigned", "sonatypePrepare", "sonatypeBundleUpload")
      case Target.Release   => Seq("+publishSigned", "sonatypeBundleRelease")
      case Target.Promote   => Seq("sonatypeRelease")
      case Target.LocalTest => Seq("+publishSigned")
    }).map(releaseStepCommandAndRemaining(_): ReleaseStep),

    Stage.PostRelease -> Seq(
      inquireVersions,
      setNextVersion,
      commitNextVersion
    ),
    Stage.Push -> Seq(
      pushChanges
    )
  ))

}
