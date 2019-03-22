package com.github.fehu.opentracing.akka

import scala.concurrent.{ ExecutionContext, Future }

import akka.actor.ActorRef
import akka.pattern
import akka.util.Timeout
import cats.{ Id, ~> }
import cats.syntax.either._
import com.gihub.fehu.opentracing.Tracing
import io.opentracing.Tracer

object AskTracing {

  class Ops(ref: ActorRef, message: Any, sender: ActorRef)(
    implicit
    trace0: Tracing[Id, TracingMessage.MaybeDeferredTraced],
    tracer: Tracer,
    setup: Tracing.TracingSetup,
    timeout: Timeout,
    executionContext: ExecutionContext
  ) {

    def tracing: Tracing.Interface[Future[Any]] = trace(message)

    private def ask(msg: Any) = pattern.ask(ref, msg, sender)
    private lazy val trace = trace0.map[λ[* => Future[Any]]](
      λ[TracingMessage.MaybeDeferredTraced ~> λ[* => Future[Any]]]{
        case Left(msg) => ask(msg)
        case Right(later) =>
          val (msg, scope) = later.value
          val future = ask(msg)
          future.onComplete(res => setup.beforeStopScope(Either.fromTry(res))(scope))
          scope.close()
          future
      }
    )
  }

}
