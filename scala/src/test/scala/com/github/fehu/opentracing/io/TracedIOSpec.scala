package com.github.fehu.opentracing.io

import scala.concurrent.ExecutionContext

import cats.effect.{ ContextShift, Effect, IO, Timer }

import com.github.fehu.opentracing.transformer._
import com.github.fehu.opentracing.{ TraceSpec, Traced }

class TracedIOSpec extends TraceSpec[TracedIO] {
  implicit lazy val tracedRunParams: Traced.RunParams =
    Traced.RunParams(mockTracer, Traced.Hooks(), Traced.ActiveSpan.empty)
  implicit lazy val effect: Effect[TracedIO] = tracedTEffectInstance

  implicit lazy val csIO: ContextShift[IO] = IO.contextShift(ExecutionContext.global)
  implicit lazy val cs: ContextShift[TracedIO] = tracedTContextShiftInstance

  implicit lazy val timerIO: Timer[IO] = IO.timer(ExecutionContext.global)
  implicit lazy val timer: Timer[TracedIO] = tracedTTimerInstance
}
