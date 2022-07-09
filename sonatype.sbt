import xerial.sbt.Sonatype._

inThisBuild(Seq(
  sonatypeProfileName := organization.value,
  sonatypeProjectHosting := Some(GitHubHosting("fehu", "opentracing-scala", "kdn.kovalev@gmail.com")),
  sonatypeCredentialHost := "s01.oss.sonatype.org",
  sonatypeRepository := "https://s01.oss.sonatype.org/",
  publishMavenStyle := true,
  licenses := Seq(License.MIT),
  homepage := None,
  scmInfo := Some(
    ScmInfo(
      url("https://github.com/fehu/opentracing-scala"),
      "scm:git@github.com:fehu/opentracing-scala.git"
    )
  ),
  developers := List(
    Developer(
      id = "fehu",
      name = "Dmitry Kovalev",
      email = "kdn.kovalev@gmail.com",
      url = url("https://github.com/fehu")
    )
  ),
  usePgpKeyHex("A39486CEF95074A56BAF559B8E1AFFF8C89BBCD6"),
  credentials += Credentials("Sonatype Nexus Repository Manager", "s01.oss.sonatype.org", sonatypeUser, sonatypePassword)
))

lazy val sonatypeUser = Option(System.getenv("SONATYPE_USER")).getOrElse("")
lazy val sonatypePassword = Option(System.getenv("SONATYPE_PASSWORD")).getOrElse("")
