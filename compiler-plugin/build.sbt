name := "opentracing-jaeger-scalac-implicits"

version := "0.1.1"

libraryDependencies ++= Seq(
  Dependencies.`scala-compiler`.value,
  Dependencies.`jaeger-client`
  // [Assembly] slf4j implementation is not included despite the warnings that appear on plugin usage
)

// Assembly - dependencies aren't provided by sbt for compiler plugins

enablePlugins(AssemblyPlugin)

assemblyOption in assembly := (assemblyOption in assembly).value.copy(includeScala = false)

packageBin in Compile := (assembly in Compile).value
