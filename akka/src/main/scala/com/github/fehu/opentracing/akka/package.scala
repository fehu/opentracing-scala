package com.github.fehu.opentracing

import scala.concurrent.ExecutionContext

import _root_.akka.actor.ActorRef
import _root_.akka.util.Timeout
import cats.Id
import io.opentracing.Tracer

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

}
