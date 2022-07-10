name := "opentracing-scalac-implicits-jaeger"

libraryDependencies ++= Seq(
  Dependencies.`scala-compiler`.value,
  Dependencies.`jaeger-client`
  // [Assembly] slf4j implementation is not included despite the warnings that appear on plugin usage
)

// Assembly - dependencies aren't provided by sbt for compiler plugins

enablePlugins(AssemblyPlugin)

assembly / assemblyOption := (assembly / assemblyOption).value.withIncludeScala(true)
assembly / assemblyMergeStrategy := {
  case PathList("META-INF", _*)            => MergeStrategy.discard
  case PathList("rootdoc.txt")             => MergeStrategy.discard
  case PathList("LICENSE" | "NOTICE")      => MergeStrategy.deduplicate
  case PathList("javax", "annotation", _*) => MergeStrategy.first
  case _                                   => MergeStrategy.singleOrError
}

Compile / packageBin := (Compile / assembly).value

// // // Release // // //

crossVersion := CrossVersion.full

releaseTagName := s"plugin-v${version.value}"
releaseUseGlobalVersion := false

sonatypeBundleDirectory := target.value / "sonatype-staging" / version.value
