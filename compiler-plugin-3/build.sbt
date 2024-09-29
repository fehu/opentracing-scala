name := "scala3-compiler-tracing-jaeger"

libraryDependencies ++= Seq(
  Dependencies.`scala-compiler`.value,
  Dependencies.`jaeger-client`
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

releaseTagName          := s"plugin3-v${version.value}"
releaseUseGlobalVersion := false

sonatypeBundleDirectory := target.value / "sonatype-staging" / version.value
