package com.github.fehu.opentracing.akka

import cats.arrow.FunctionK
import cats.{ Id, Later, ~> }
import com.gihub.fehu.opentracing.Tracing
import io.opentracing.{ Scope, Tracer }
import io.opentracing.contrib.akka.TracedMessage

object TracingMessage {
  type MaybeDeferredTraced[A] = Either[A, Later[(TracedMessage[A], Scope)]]
}

/**
 * Wraps a message to be sent to another actor.
 * The span will always start active, it's your responsibility to close it on sender's thread after sending the message.
 */
class TracingMessage(implicit setup: Tracing.TracingSetup) extends Tracing[Id, TracingMessage.MaybeDeferredTraced] {
  import TracingMessage._
  // `activate` is ignored
  protected def build(spanBuilder: Tracer.SpanBuilder, activate: Boolean): Id ~> MaybeDeferredTraced =
    λ[FunctionK[Id, MaybeDeferredTraced]] { msg =>
      Right(Later{
        val scope = setup.beforeStart(spanBuilder).startActive(true)
        new TracedMessage(msg, scope.span()) -> scope
      })
    }
  protected def stub: Id ~> MaybeDeferredTraced = λ[Id ~> MaybeDeferredTraced](Left(_))
}
