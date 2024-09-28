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
  case PathList("META-INF", xs @ _*) if xs.lastOption.exists(_.endsWith(".class")) =>
    MergeStrategy.discard
  case x =>
    val oldStrategy = (ThisBuild / assemblyMergeStrategy).value
    oldStrategy(x)
}

Compile / packageBin := (Compile / assembly).value

// // // Release // // //

crossVersion := CrossVersion.full

releaseTagName          := s"plugin-v${version.value}"
releaseUseGlobalVersion := false

sonatypeBundleDirectory := target.value / "sonatype-staging" / version.value
