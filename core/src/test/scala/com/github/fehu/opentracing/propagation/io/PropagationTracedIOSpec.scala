package com.github.fehu.opentracing.propagation.io

import cats.effect.std.Dispatcher
import cats.effect.unsafe.implicits.global

import com.github.fehu.opentracing.Traced
import com.github.fehu.opentracing.propagation.PropagationSpec
import com.github.fehu.opentracing.transformer.TracedIO

class PropagationTracedIOSpec extends PropagationSpec[TracedIO] {
  implicit lazy val tracedSetup: Traced.Setup = Traced.Setup.default(mockTracer)
  implicit lazy val tracedSpan: Traced.ActiveSpan = Traced.ActiveSpan.empty

  def dispatcher: Dispatcher[TracedIO] = TracedIO.Dispatcher.fromScope.allocated.unsafeRunSync()._1
}
