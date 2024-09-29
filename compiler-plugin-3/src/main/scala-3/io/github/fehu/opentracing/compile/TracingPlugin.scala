package io.github.fehu.opentracing.compile

import dotty.tools.dotc.core.{ Contexts, Phases }
import dotty.tools.dotc.plugins.ResearchPlugin
import io.jaegertracing.Configuration
import io.jaegertracing.Configuration.SamplerConfiguration
import io.jaegertracing.internal.samplers.ConstSampler
import io.opentracing.Tracer

class TracingPlugin extends ResearchPlugin:
  import TracingPlugin.tracer

  val name: String = "Tracing"
  val description: String =
    "Traces implicit searches performed by scalac and reports them to local jaegertracing backend"

  override def init(options: List[String], plan: List[List[Phases.Phase]])(using
      Contexts.Context
  ): List[List[Phases.Phase]] =
    plan.map {
      _.map {
        case phase if phase.phaseName == "inlining" =>
          TracedInlining(tracer)
        case phase if phase.phaseName == "typer" =>
          TracedTyper(tracer)
        case other =>
          other
      }
    }

end TracingPlugin

object TracingPlugin:
  val tracerServiceName = "scala3-compiler"

  protected lazy val tracer: Tracer = Configuration
    .fromEnv(tracerServiceName)
    .withSampler(
      SamplerConfiguration
        .fromEnv()
        .withType(ConstSampler.TYPE)
        .withParam(1)
    )
    .getTracer
