package com.github.fehu.opentracing.io

import scala.concurrent.ExecutionContext

import cats.effect.{ ContextShift, Effect, IO, Timer }

import com.github.fehu.opentracing.transformer._
import com.github.fehu.opentracing.TraceSpec

class TracedIOSpec extends TraceSpec[TracedIO] {
  implicit lazy val effect: Effect[TracedIO] = TracedT.tracedTEffectInstance

  implicit lazy val csIO: ContextShift[IO] = IO.contextShift(ExecutionContext.global)
  implicit lazy val cs: ContextShift[TracedIO] = TracedT.tracedTContextShiftInstance

  implicit lazy val timerIO: Timer[IO] = IO.timer(ExecutionContext.global)
  implicit lazy val timer: Timer[TracedIO] = TracedT.tracedTTimerInstance
}
