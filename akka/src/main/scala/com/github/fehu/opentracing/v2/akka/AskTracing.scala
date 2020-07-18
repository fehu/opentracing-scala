package com.github.fehu.opentracing.v2.akka


import akka.actor.ActorRef
import akka.pattern
import akka.util.Timeout
import cats.effect.{ Async, ContextShift }
import cats.syntax.flatMap._
import cats.syntax.functor._

import com.github.fehu.opentracing.v2.Traced
import com.github.fehu.opentracing.v2.syntax._

object AskTracing {
  class Ops[F[_]: Async: ContextShift: Traced](ref: ActorRef, message: Any, sender: ActorRef)
                                              (implicit timeout: Timeout) {
    def traced: F[Any] = trace0(locally)

    def trace(op: String, tags: Traced.Tag*): F[Any] = trace0(_.trace(op, tags: _*))

    private def trace0[R](continue: F[Any] => F[R]): F[R] =
      for {
        ctx <- Traced.currentSpan.context
        res = Async.fromFuture(
                Async[F].delay{ pattern.ask(ref, TracedMessage(message, ctx), sender) }
              )
        r <- continue(res)
      } yield r
  }
}