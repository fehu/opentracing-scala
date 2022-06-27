package com.github.fehu.opentracing.io

import cats.effect.std.Dispatcher
import cats.effect.unsafe.implicits.global

import com.github.fehu.opentracing.transformer._
import com.github.fehu.opentracing.TraceSpec

class TracedIOSpec extends TraceSpec[TracedIO] {
  val dispatcher: Dispatcher[TracedIO] = TracedIO.dispatcher(tracedRunParams).allocated.unsafeRunSync()._1
}
