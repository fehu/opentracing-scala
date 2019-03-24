package com.github.fehu.opentracing.concurrent

import scala.concurrent.ExecutionContext

import io.opentracing.{ Span, Tracer }
import com.github.fehu.opentracing.activate


trait TracingExecutionContext extends ExecutionContext {
  val span: Span
  val tracer: Tracer
}

object TracingExecutionContext {
  class Delegate(val span: Span, underlying: ExecutionContext)(implicit val tracer: Tracer) extends TracingExecutionContext {
    def execute(runnable: Runnable): Unit =
      underlying.execute(() =>
        activate(span) { runnable.run() }
      )
    def reportFailure(cause: Throwable): Unit = underlying.reportFailure(cause)
  }

  object Delegate {
    def active(underlying: ExecutionContext)(implicit tracer: Tracer): Builder = new Builder(underlying)

    class Builder(underlying: ExecutionContext)(implicit tracer: Tracer) {
      implicit def context: Delegate = new Delegate(tracer.activeSpan(), underlying)
    }
  }
}