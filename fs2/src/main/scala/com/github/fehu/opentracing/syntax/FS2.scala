package com.github.fehu.opentracing.syntax

import _root_.fs2.Stream
import cats.~>
import cats.effect.{ Bracket, Resource }
import cats.syntax.apply._
import io.opentracing.SpanContext
import io.opentracing.propagation.Format

import com.github.fehu.opentracing.Traced
import com.github.fehu.opentracing.Traced.ActiveSpan

object FS2 {

  final implicit class TracedFs2StreamOps[F[_]: Bracket[*[_], Throwable], A](stream: Stream[F, A])(implicit t: Traced[F]) {
    def traceLifetime(operation: String, tags: Traced.Tag*): Stream[F, A] =
      for {
        (span, finish) <- Stream eval t.spanResource(operation, tags: _*).allocated
        a <- stream.translate(λ[F ~> F](t.recoverCurrentSpan(span) *> _))
          .onFinalize(finish)
      } yield a

    def traceUsage(operation: String, tags: Traced.Tag*): Stream[F, A] =
      tracingElems(_ => t.spanResource(operation, tags: _*))

    def traceUsage(trace: A => Traced.Operation.Builder): Stream[F, A] =
      tracingElems { a => t.spanResource(trace(a)) }

    def traceUsageInjecting(context: A => SpanContext, trace: A => Traced.Operation.Builder): Stream[F, A] =
      tracingElems { a => t.injectContext(context(a)).spanResource(trace(a)) }

    def traceUsageInjectingOpt(context: A => Option[SpanContext], trace: A => Traced.Operation.Builder, traceEmpty: Boolean = true): Stream[F, A] =
      tracingElems { a =>
        context(a).fold(
          if (traceEmpty) t.spanResource(trace(a)) else Resource.pure(ActiveSpan.empty)
        )(
          t.injectContext(_).spanResource(trace(a))
        )
      }

    def traceUsageInjectingFrom[C](format: Format[C])(carrier: A => C, trace: A => Traced.Operation.Builder): Stream[F, A] =
      tracingElems { a =>
        t.injectContextFrom(format)(carrier(a)).spanResource(trace(a))
      }

    def traceUsageInjectingFromOpt[C](format: Format[C])(carrier: A => Option[C], trace: A => Traced.Operation.Builder, traceEmpty: Boolean = true): Stream[F, A] =
      tracingElems { a =>
        carrier(a).fold(
          if (traceEmpty) t.spanResource(trace(a)) else Resource.pure(ActiveSpan.empty)
        )(
          t.injectContextFrom(format)(_).spanResource(trace(a))
        )
      }

    def tracedLog(f: A => Seq[(String, Any)]): Stream[F, A] =
      stream.evalTap(a => t.currentSpan.log(f(a): _*))

    def tracedElemLog: Stream[F, A] = stream.evalTap(t.currentSpan log _.toString)

    private def tracingElems(f: A => Resource[F, Traced.ActiveSpan]): Stream[F, A] =
      stream.flatMap(a => Stream.resource(f(a)).as(a))
  }

}
