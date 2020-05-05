package com.github.fehu.opentracing

import scala.concurrent.{ ExecutionContext, Future }

import _root_.akka.actor.{ ActorContext, ActorRef }
import _root_.akka.util.Timeout
import io.opentracing.Tracer

package object akka {

  def ask[F[_]](actorRef: ActorRef, message: Any, sender: ActorRef)(
    implicit
    trace: AskTracing[F],
    tracer: Tracer,
    setup: Tracing.TracingSetup,
    timeout: Timeout,
    executionContext: ExecutionContext
  ): AskTracing.Ops[F] = new AskTracing.Ops(actorRef, message, sender)

  def ask[F[_]](actorRef: ActorRef, message: Any)(
    implicit
    trace: AskTracing[F],
    tracer: Tracer,
    setup: Tracing.TracingSetup,
    timeout: Timeout,
    executionContext: ExecutionContext,
    sender: ActorRef = ActorRef.noSender
  ): AskTracing.Ops[F] = new AskTracing.Ops(actorRef, message, sender)


  implicit def tracingMessage(implicit setup: Tracing.TracingSetup): TracingMessage = new TracingMessage
  implicit def askTracingFuture(implicit setup: Tracing.TracingSetup, timeout: Timeout, ec: ExecutionContext): AskTracing[λ[* => () => Future[Any]]] = AskTracing.askTracingFuture

  implicit class TracingMessagesOps(ref: ActorRef) {
    def tellTracing(message: Any)(implicit context: ActorContext, tracer: Tracer): Unit =
      ref.tell(TracedMessage(message, tracer.activeSpan()), context.self)

    /** Alias for [[tellTracing]]. */
    def !*(message: Any)(implicit context: ActorContext, tracer: Tracer): Unit = tellTracing(message)

    def forwardTracing(message: Any)(implicit context: ActorContext, tracer: Tracer): Unit =
      ref.tell(TracedMessage(message, tracer.activeSpan()), context.sender())
  }
}
