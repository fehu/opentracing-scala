package com.github.fehu.opentracing.io

import scala.concurrent.ExecutionContext

import cats.effect.{ ContextShift, Effect, IO, Timer }

import com.github.fehu.opentracing.transformer._
import com.github.fehu.opentracing.{ TraceSpec, Traced }

class TracedTIOSpec extends TraceSpec[TracedTIO] {
  implicit lazy val tracedRunParams: Traced.RunParams =
    Traced.RunParams(mockTracer, Traced.Hooks(), Traced.ActiveSpan.empty)
  implicit lazy val effect: Effect[TracedTIO] = tracedTEffectInstance

  implicit lazy val csIO: ContextShift[IO] = IO.contextShift(ExecutionContext.global)
  implicit lazy val cs: ContextShift[TracedTIO] = tracedTContextShiftInstance

  implicit lazy val timerIO: Timer[IO] = IO.timer(ExecutionContext.global)
  implicit lazy val timer: Timer[TracedTIO] = tracedTTimerInstance
}
