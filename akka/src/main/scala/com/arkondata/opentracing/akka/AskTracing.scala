package com.arkondata.opentracing.akka

import scala.concurrent.{ ExecutionContext, Future }

import akka.actor.ActorRef
import akka.pattern
import akka.util.Timeout
import cats.{ Id, ~> }
import cats.syntax.either._
import com.arkondata.opentracing.{ Tracing, util }
import io.opentracing.Tracer

final class AskTracing[F[_]](val build: (ActorRef, Timeout, ExecutionContext, AskTracing.Sender) => Tracing[Id, F]) extends AnyVal {
  def map[G[_]](fk: F ~> G): AskTracing[G] = new AskTracing((ref, t, ec, sender) => build(ref, t, ec, sender).map(fk))
}

object AskTracing {
  type Sender = ActorRef

  class Ops[F[_]](ref: ActorRef, message: Any, sender: ActorRef)(
    implicit
    ask: AskTracing[F],
    tracer: Tracer,
    setup: Tracing.TracingSetup,
    timeout: Timeout,
    executionContext: ExecutionContext
  ) {
    def tracing: Tracing.Interface[F[Any]] = ask.build(ref, timeout, executionContext, sender)(message)
  }


  implicit def askTracingFuture(implicit setup: Tracing.TracingSetup): AskTracing[λ[* => () => Future[Any]]] =
    new AskTracing((ref, timeout, execContext, sender) =>
      tracingMessage.map[λ[* => () => Future[Any]]](
        λ[TracingMessage.MaybeDeferredTraced ~> λ[* => () => Future[Any]]]{
          case Left(msg) => () => pattern.ask(ref, msg, sender)(timeout)
          case Right(later) => () => {
            val msg = later.value
            val future = pattern.ask(ref, msg, sender)(timeout)
            future.onComplete { res =>
              try setup.beforeStop(Either.fromTry(res))(msg.span)
              finally util.finishSpanSafe(msg.span)
            }(execContext)
            future
          }
        }
      )
    )

}
