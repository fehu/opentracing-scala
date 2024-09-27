package io.github.fehu.opentracing.propagation

import cats.effect.std.Dispatcher

import io.github.fehu.opentracing.transformer.TracedIO
import io.github.fehu.opentracing.{ IOSpec, Traced }

class PropagationTracedIOSpec extends PropagationSpec[TracedIO] with IOSpec {
  implicit lazy val tracedSetup: Traced.Setup     = Traced.Setup.default(mockTracer)
  implicit lazy val tracedSpan: Traced.ActiveSpan = Traced.ActiveSpan.empty

  def dispatcher: Dispatcher[TracedIO] = TracedIO.Dispatcher.fromScope.allocated.unsafeRunSync()._1
}
