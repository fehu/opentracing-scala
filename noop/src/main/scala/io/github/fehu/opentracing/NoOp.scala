package io.github.fehu.opentracing

import io.opentracing.noop.NoopTracerFactory

import io.github.fehu.opentracing.util.TracerExt

object NoOp {
  def apply(): TracerExt = TracerExt(NoopTracerFactory.create())
}
