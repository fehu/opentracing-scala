package io.github.fehu.opentracing.compile

import dotty.tools.dotc.Run
import dotty.tools.dotc.core.Contexts
import dotty.tools.dotc.typer.TyperPhase
import io.opentracing.Tracer

class TracedTyper(tracer: Tracer) extends TyperPhase with TracedPhase(tracer):

  override def enterSyms(using ctx: Contexts.Context)(using
      subphase: Run.SubPhase
  ): Boolean =
    trace("enterSyms", "subphase" -> subphase.name)(super.enterSyms)

  override def typeCheck(using Contexts.Context)(using
      subphase: Run.SubPhase
  ): Boolean =
    trace("typeCheck", "subphase" -> subphase.name)(super.typeCheck)

  override def javaCheck(using Contexts.Context)(using
      subphase: Run.SubPhase
  ): Boolean =
    trace("javaCheck", "subphase" -> subphase.name)(super.javaCheck)

end TracedTyper
