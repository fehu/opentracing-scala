package com.github.fehu.opentracing.monix

import cats.effect.{ ContextShift, Effect, Timer }
import _root_.monix.eval.Task

import com.github.fehu.opentracing.transformer._
import com.github.fehu.opentracing.transformer.Monix.TracedTask
import com.github.fehu.opentracing.{ TraceSpec, Traced }

class TracedTaskSpec extends TraceSpec[TracedTask] {
  implicit lazy val tracedRunParams: Traced.RunParams =
    Traced.RunParams(mockTracer, Traced.Hooks(), Traced.ActiveSpan.empty)

  import _root_.monix.execution.Scheduler.Implicits.global
  implicit val effect: Effect[TracedTask] = tracedTEffectInstance[Task]

  implicit val cs: ContextShift[TracedTask] = tracedTContextShiftInstance
  implicit val timer: Timer[TracedTask] = tracedTTimerInstance
}
