package io.github.fehu.opentracing

import cats.effect.std.Dispatcher

import io.github.fehu.opentracing.transformer.*

class TracedIOSpec extends TraceSpec[TracedIO] with IOSpec {
  val dispatcher: Dispatcher[TracedIO] = TracedIO.Dispatcher.fromScope.allocated.unsafeRunSync()._1

  override protected def ioRuntimeComputeThreads: Int = 2
}
