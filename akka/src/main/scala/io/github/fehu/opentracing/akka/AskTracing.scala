package io.github.fehu.opentracing.akka

import akka.actor.ActorRef
import akka.pattern
import akka.util.Timeout
import cats.effect.Async
import cats.syntax.flatMap.*
import cats.syntax.functor.*

import io.github.fehu.opentracing.Traced
import io.github.fehu.opentracing.syntax.*

object AskTracing {
  class Ops[F[_]: Async: Traced](ref: ActorRef, message: Any, sender: ActorRef)(implicit timeout: Timeout) {
    def traced: F[Any] = trace0

    def trace(op: String, tags: Traced.Tag*): F[Any] = trace0.trace(op, tags*)

    private def trace0: F[Any] =
      for {
        ctx <- Traced.currentSpan.context
        res <- Async[F].fromFuture(
          Async[F].delay(pattern.ask(ref, TracedMessage(message, ctx), sender))
        )
      } yield res
  }
}
