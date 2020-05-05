package com.github.fehu.opentracing.akka

import scala.concurrent.{ ExecutionContext, Future }

import akka.actor.ActorRef
import akka.pattern
import akka.util.Timeout
import cats.{ Id, ~> }
import cats.syntax.either._
import com.github.fehu.opentracing.{ Tracing, util }
import io.opentracing.Tracer

final class AskTracing[F[_]](val build: (ActorRef, AskTracing.Sender) => Tracing[Id, F]) extends AnyVal {
  def map[G[_]](fk: F ~> G): AskTracing[G] = new AskTracing((ref, sender) => build(ref, sender).map(fk))
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
    def tracing: Tracing.Interface[F[Any]] = ask.build(ref, sender)(message)
  }


  implicit def askTracingFuture(implicit setup: Tracing.TracingSetup, timeout: Timeout, ec: ExecutionContext): AskTracing[λ[* => () => Future[Any]]] =
    new AskTracing((ref, sender) =>
      tracingMessage.map[λ[* => () => Future[Any]]](
        λ[TracingMessage.MaybeDeferredTraced ~> λ[* => () => Future[Any]]]{
          case Left(msg) => () => pattern.ask(ref, msg, sender)
          case Right(later) => () => {
            val msg = later.value
            val future = pattern.ask(ref, msg, sender)
            future.onComplete { res =>
              try setup.beforeStop(Either.fromTry(res))(msg.span)
              finally util.finishSpanSafe(msg.span)
            }
            future
          }
        }
      )
    )

}
