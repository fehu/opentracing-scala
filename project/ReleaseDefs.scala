object ReleaseDefs {
  sealed trait Stage {
    val lowercase: String = toString.toLowerCase
  }
  object Stage {
    case object Check       extends Stage
    case object Test        extends Stage
    case object PreRelease  extends Stage
    case object Release     extends Stage
    case object PostRelease extends Stage
    case object Push        extends Stage

    def parse(s: String): Stage = (normEnum(s): @unchecked) match {
      case Check.lowercase       => Check
      case Test.lowercase        => Test
      case PreRelease.lowercase  => PreRelease
      case Release.lowercase     => Release
      case PostRelease.lowercase => PostRelease
      case Push.lowercase        => Push
    }
  }

  sealed trait Target {
    val lowercase: String = toString.toLowerCase
  }
  object Target {
    case object Staging   extends Target
    case object Release   extends Target
    case object Promote   extends Target
    case object LocalTest extends Target

    def parse(s: String): Target = (normEnum(s): @unchecked) match {
      case Staging.lowercase   => Staging
      case Release.lowercase   => Release
      case Promote.lowercase   => Promote
      case LocalTest.lowercase => LocalTest
    }
  }

  private def normEnum = (_: String).replaceAll("[\\s_-]+", "").toLowerCase
}
