package com.arkondata.opentracing.akka

import scala.util.Try

import cats.arrow.FunctionK
import cats.{ Id, Later, ~> }
import com.arkondata.opentracing.Tracing
import io.opentracing.Tracer

object TracingMessage {
  type MaybeDeferredTraced[A] = Either[A, Later[TracedMessage[A]]]
}

/**
 * Wraps a message to be sent to another actor.
 * The span will always start inactive.
 */
class TracingMessage(implicit setup: Tracing.TracingSetup) extends Tracing[Id, TracingMessage.MaybeDeferredTraced] {
  import TracingMessage._
  protected def build(spanBuilder: Tracer.SpanBuilder, activate: Boolean): Id ~> MaybeDeferredTraced =
    λ[FunctionK[Id, MaybeDeferredTraced]] { msg =>
      Right(Later{
        val span = setup.beforeStart(spanBuilder).start()
        Try { setup.justAfterStart(span) }
        TracedMessage(msg, span)
      })
    }
  protected def noTrace: Id ~> MaybeDeferredTraced = λ[Id ~> MaybeDeferredTraced](Left(_))
}
