package com.github.fehu.opentracing.propagation.io

import cats.effect.std.Dispatcher
import cats.effect.unsafe.implicits.global

import com.github.fehu.opentracing.Traced
import com.github.fehu.opentracing.propagation.PropagationSpec
import com.github.fehu.opentracing.transformer.TracedIO
import com.github.fehu.opentracing.util.ErrorLogger

class PropagationTracedIOSpec extends PropagationSpec[TracedIO] {
  implicit lazy val tracedRunParams: Traced.RunParams =
    Traced.RunParams(mockTracer, Traced.Hooks(), Traced.ActiveSpan.empty, ErrorLogger.stdout)

  def dispatcher: Dispatcher[TracedIO] = TracedIO.dispatcher(tracedRunParams).allocated.unsafeRunSync()._1
}
