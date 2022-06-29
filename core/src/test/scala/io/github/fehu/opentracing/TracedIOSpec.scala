package io.github.fehu.opentracing

import cats.effect.std.Dispatcher
import cats.effect.unsafe.implicits.global

import io.github.fehu.opentracing.transformer.*

class TracedIOSpec extends TraceSpec[TracedIO] {
  val dispatcher: Dispatcher[TracedIO] = TracedIO.Dispatcher.fromScope.allocated.unsafeRunSync()._1
}
