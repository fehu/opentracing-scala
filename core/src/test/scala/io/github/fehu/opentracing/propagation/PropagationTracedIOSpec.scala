package io.github.fehu.opentracing.propagation

import cats.effect.std.Dispatcher
import cats.effect.unsafe.implicits.global

import io.github.fehu.opentracing.Traced
import io.github.fehu.opentracing.transformer.TracedIO

class PropagationTracedIOSpec extends PropagationSpec[TracedIO] {
  implicit lazy val tracedSetup: Traced.Setup = Traced.Setup.default(mockTracer)
  implicit lazy val tracedSpan: Traced.ActiveSpan = Traced.ActiveSpan.empty

  def dispatcher: Dispatcher[TracedIO] = TracedIO.Dispatcher.fromScope.allocated.unsafeRunSync()._1
}
