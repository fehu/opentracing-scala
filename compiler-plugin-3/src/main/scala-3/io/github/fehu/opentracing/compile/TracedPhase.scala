package io.github.fehu.opentracing.compile

import dotty.tools.dotc.CompilationUnit
import dotty.tools.dotc.core.Contexts
import dotty.tools.dotc.core.Phases.Phase
import io.opentracing.Tracer

trait TracedPhase(tracer: Tracer) extends Phase:
  override def runOn(units: List[CompilationUnit])(using
      Contexts.Context
  ): List[CompilationUnit] =
    trace("runOn", "on" -> units.mkString("[", ", ", "]"))(super.runOn(units))

  abstract override def run(using Contexts.Context): Unit =
    trace("run")(super.run)

  protected def trace[R](name: String, tags: (String, Any)*)(f: => R)(using
      ctx: Contexts.Context
  ): R =
    val b = tracer
      .buildSpan(s"$phaseName.$name")
      .withTag("mode", ctx.mode.toString)
      .withTag("owner", ctx.owner.toString)
      .withTag("compilationUnit", ctx.compilationUnit.toString)
      .withTag("isImportContext", ctx.isImportContext)
      .withTag("isClassDefContext", ctx.isClassDefContext)
      .withTag("isInlineContext", ctx.isInlineContext)
      .withTag("source", ctx.source.path)
      .withTag("tree", ctx.tree.toString)
    val span = tags
      .foldLeft(b) { case (b0, (k, v)) => b0.withTag(k, v.toString) }
      .start()

    tracer.activateSpan(span)

    try f
    catch
      case err: Throwable =>
        span.setTag("error", true)
        span.setTag("error.message", err.getMessage)
        throw err
    finally span.finish()
  end trace

end TracedPhase
