package com.github.fehu.opentracing

import scala.concurrent.ExecutionContext

import _root_.akka.actor.{ ActorContext, ActorRef }
import _root_.akka.util.Timeout
import cats.Id
import io.opentracing.{ Span, Tracer }

package object akka {
  import TracingMessage._

  def ask(actorRef: ActorRef, message: Any, sender: ActorRef)(
    implicit
    trace: Tracing[Id, MaybeDeferredTraced],
    tracer: Tracer,
    setup: Tracing.TracingSetup,
    timeout: Timeout,
    executionContext: ExecutionContext
  ): AskTracing.Ops = new AskTracing.Ops(actorRef, message, sender)

  def ask(actorRef: ActorRef, message: Any)(
    implicit
    trace: Tracing[Id, MaybeDeferredTraced],
    tracer: Tracer,
    setup: Tracing.TracingSetup,
    timeout: Timeout,
    executionContext: ExecutionContext,
    sender: ActorRef = ActorRef.noSender
  ): AskTracing.Ops = new AskTracing.Ops(actorRef, message, sender)


  implicit def tracingMessage(implicit setup: Tracing.TracingSetup): TracingMessage = new TracingMessage

  implicit class TracingMessagesOps(ref: ActorRef) {
    def tellTracing(message: Any)(implicit context: ActorContext, tracer: Tracer): Unit =
      ref.tell(TracedMessage(message, tracer.activeSpan()), context.self)

    /** Alias for [[tellTracing]]. */
    def !*(message: Any)(implicit context: ActorContext, tracer: Tracer): Unit = tellTracing(message)

    def forwardTracing(message: Any)(implicit context: ActorContext, tracer: Tracer): Unit =
      ref.tell(TracedMessage(message, tracer.activeSpan()), context.sender())
  }
}
