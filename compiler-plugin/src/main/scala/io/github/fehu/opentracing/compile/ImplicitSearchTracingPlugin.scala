package io.github.fehu.opentracing.compile

import scala.collection.mutable
import scala.reflect.internal.util.NoSourceFile
import scala.tools.nsc.Global
import scala.tools.nsc.plugins.{ Plugin, PluginComponent }

import io.jaegertracing.Configuration
import io.jaegertracing.Configuration.SamplerConfiguration
import io.jaegertracing.internal.samplers.ConstSampler
import io.opentracing.{ Span, Tracer }

class ImplicitSearchTracingPlugin(val global: Global) extends Plugin {
  import ImplicitSearchTracingPlugin.tracer
  import global._

  val name: String = "TracingImplicitSearch"
  val description: String = "Traces implicit searches performed by scalac and reports them to local jaegertracing backend"
  val components: List[PluginComponent] = Nil

  private val spansStack = mutable.Stack.empty[Span]

  analyzer.addAnalyzerPlugin(new ImplicitsTracingAnalyzer)

  class ImplicitsTracingAnalyzer extends analyzer.AnalyzerPlugin {
    override def pluginsNotifyImplicitSearch(search: global.analyzer.ImplicitSearch): Unit = {
      val pos = search.pos
      val code = if (pos.source != NoSourceFile) pos.lineContent else "<NoSourceFile>"
      val span = tracer
        .buildSpan(showShort(search.pt))
        .asChildOf(spansStack.headOption.orNull)
        .withTag("type", search.pt.safeToString)
        .withTag("file", pos.source.path)
        .withTag("line", pos.line)
        .withTag("code", code)
        .withTag("pos",  pos.toString)
        .start()
      spansStack.push(span)
      super.pluginsNotifyImplicitSearch(search)
    }

    override def pluginsNotifyImplicitSearchResult(result: global.analyzer.SearchResult): Unit = {
      val span = spansStack.pop()
      span.setTag("isSuccess", result.isSuccess)
      val symb = result.tree.symbol
      val providedBy = if (symb eq null) typeNames.NO_NAME.toString
                       else {
                          val rt = result.tree.tpe.resultType
                          val targs = if (rt.typeArgs.nonEmpty) rt.typeArgs.mkString("[", ", ", "]") else ""
                          s"${symb.kindString} ${symb.fullNameString}$targs"
                      }
      span.setTag("provided by", providedBy)
      result.subst.from zip result.subst.to foreach { case (from, to) =>
        span.setTag(s"type subst ${from.name}", to.toLongString)
      }
      span.finish()
      if (spansStack.isEmpty) {
        // A workaround for `ClassNotFoundException`s on closing the tracer.
        // Found at [[https://github.com/jaegertracing/jaeger-client-java/issues/593]]
        Class.forName("io.jaegertracing.internal.reporters.RemoteReporter$CloseCommand")
        Class.forName("io.jaegertracing.agent.thrift.Agent$Client")
      }
      super.pluginsNotifyImplicitSearchResult(result)
    }

    private def showName(name0: String): String =
      name0.takeWhile(_ != '{').split('.').reverse match {
        case Array("Aux", name, _*) => name
        case Array(name, _*) => name
      }
    private def showShort(tpe: Type): String = showName(tpe.typeConstructor.toString)
  }
}

object ImplicitSearchTracingPlugin {
  val tracerServiceName = "implicit search"

  protected val tracer: Tracer = Configuration
    .fromEnv(tracerServiceName)
    .withSampler(
      SamplerConfiguration.fromEnv()
        .withType(ConstSampler.TYPE)
        .withParam(1)
    )
    .getTracer
}
