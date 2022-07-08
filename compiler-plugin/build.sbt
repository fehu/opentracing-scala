name := "opentracing-jaeger-scalac-implicits"

version := "0.1.4-SNAPSHOT"

libraryDependencies ++= Seq(
  Dependencies.`scala-compiler`.value,
  Dependencies.`jaeger-client`
  // [Assembly] slf4j implementation is not included despite the warnings that appear on plugin usage
)

// Assembly - dependencies aren't provided by sbt for compiler plugins

enablePlugins(AssemblyPlugin)

assembly / assemblyOption := (assembly / assemblyOption).value.withIncludeScala(true)

Compile / packageBin := (Compile / assembly).value
