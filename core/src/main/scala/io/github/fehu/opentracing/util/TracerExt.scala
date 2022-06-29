package io.github.fehu.opentracing.util

import scala.language.implicitConversions

import io.opentracing.Tracer

import io.github.fehu.opentracing.Traced


final case class TracerExt(val tracer: Tracer) extends AnyVal {
  def setup(
    hooks: Traced.Hooks = Traced.Hooks(),
    logError: ErrorLogger = ErrorLogger.stdout
  ): Traced.Setup =
    Traced.Setup(tracer, hooks, logError)
}

object TracerExt {
  @inline implicit def toTracer(ext: TracerExt): Tracer = ext.tracer
}