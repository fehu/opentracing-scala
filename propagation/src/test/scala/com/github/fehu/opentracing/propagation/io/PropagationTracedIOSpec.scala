package com.github.fehu.opentracing.propagation.io

import cats.effect.Effect

import com.github.fehu.opentracing.Traced
import com.github.fehu.opentracing.propagation.PropagationSpec
import com.github.fehu.opentracing.transformer._

class PropagationTracedIOSpec extends PropagationSpec[TracedIO] {
  implicit lazy val tracedRunParams: Traced.RunParams =
    Traced.RunParams(mockTracer, Traced.Hooks(), Traced.ActiveSpan.empty)
  implicit lazy val effect: Effect[TracedIO] = tracedTEffectInstance
}
