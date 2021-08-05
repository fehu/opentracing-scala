package com.github.fehu.opentracing.propagation.io

import cats.effect.Effect

import com.github.fehu.opentracing.Traced
import com.github.fehu.opentracing.propagation.PropagationSpec
import com.github.fehu.opentracing.transformer._
import com.github.fehu.opentracing.util.ErrorLogger

class PropagationTracedIOSpec extends PropagationSpec[TracedIO] {
  implicit lazy val tracedRunParams: Traced.RunParams =
    Traced.RunParams(mockTracer, Traced.Hooks(), Traced.ActiveSpan.empty, ErrorLogger.stdout)
  implicit lazy val effect: Effect[TracedIO] = TracedT.tracedTEffectInstance
}
