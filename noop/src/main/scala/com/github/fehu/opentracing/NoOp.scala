package com.github.fehu.opentracing

import io.opentracing.noop.NoopTracerFactory

import com.github.fehu.opentracing.util.TracerExt

object NoOp {
  def apply(): TracerExt = TracerExt(NoopTracerFactory.create())
}
