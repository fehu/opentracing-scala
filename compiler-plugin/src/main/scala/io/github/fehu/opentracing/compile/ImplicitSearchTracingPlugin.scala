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
  val description: String =
    "Traces implicit searches performed by scalac and reports them to local jaegertracing backend"
  val components: List[PluginComponent] = Nil

  private val spansStack = mutable.Stack.empty[Span]

  analyzer.addAnalyzerPlugin(new ImplicitsTracingAnalyzer)

  // A workaround for `ClassNotFoundException`s.
  // Found at [[https://github.com/jaegertracing/jaeger-client-java/issues/593]]
  Class.forName("io.jaegertracing.internal.reporters.RemoteReporter$CloseCommand")
  Class.forName("io.jaegertracing.internal.reporters.RemoteReporter$FlushCommand")
  Class.forName("io.jaegertracing.agent.thrift.Agent$emitBatch_args")
  Class.forName("io.jaegertracing.agent.thrift.Agent$emitBatch_args$emitBatch_argsStandardScheme")
  Class.forName("io.jaegertracing.agent.thrift.Agent$Client")
  Class.forName("io.jaegertracing.thriftjava.Batch")
  Class.forName("io.jaegertracing.thriftjava.Batch$BatchStandardScheme")
  Class.forName("org.apache.thrift.protocol.TMessage")

  class ImplicitsTracingAnalyzer extends analyzer.AnalyzerPlugin {
    override def pluginsNotifyImplicitSearch(search: global.analyzer.ImplicitSearch): Unit = try {
      val pos  = search.pos
      val code = if (pos.source != NoSourceFile) pos.lineContent else "<NoSourceFile>"
      val span = tracer
        .buildSpan(showShort(search.pt))
        .asChildOf(spansStack.headOption.orNull)
        .withTag("type", search.pt.safeToString)
        .withTag("file", pos.source.path)
        .withTag("line", pos.line)
        .withTag("code", code)
        .withTag("pos", pos.toString)
        .start()
      spansStack.push(span)
      super.pluginsNotifyImplicitSearch(search)
    } catch {
      case ex: Throwable =>
        ex.printStackTrace()
        throw ex
    }

    override def pluginsNotifyImplicitSearchResult(result: global.analyzer.SearchResult): Unit = try {
      val span = spansStack.pop()
      span.setTag("isSuccess", result.isSuccess)
      val symb = result.tree.symbol
      val providedBy =
        if (symb == null || symb == NoSymbol) typeNames.NO_NAME.toString
        else {
          val rt    = result.tree.tpe.resultType
          val targs = if (rt.typeArgs.nonEmpty) rt.typeArgs.mkString("[", ", ", "]") else ""
          s"${symb.kindString} ${symb.fullNameString}$targs"
        }
      span.setTag("provided by", providedBy)
      result.subst.from zip result.subst.to foreach { case (from, to) =>
        span.setTag(s"type subst ${from.name}", to.toLongString)
      }
      span.finish()
      super.pluginsNotifyImplicitSearchResult(result)
    } catch {
      case ex: Throwable =>
        ex.printStackTrace()
        throw ex
    }

    private def showName(name0: String): String =
      name0.takeWhile(_ != '{').split('.').reverse match {
        case Array("Aux", name, _*) => name
        case Array(name, _*)        => name
      }
    private def showShort(tpe: Type): String = showName(tpe.typeConstructor.toString)
  }
}

object ImplicitSearchTracingPlugin {
  val tracerServiceName = "implicit search"

  protected val tracer: Tracer = Configuration
    .fromEnv(tracerServiceName)
    .withSampler(
      SamplerConfiguration
        .fromEnv()
        .withType(ConstSampler.TYPE)
        .withParam(1)
    )
    .getTracer
}
