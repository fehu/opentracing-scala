package com.github.fehu.opentracing

import cats.Applicative
import io.opentracing.{ SpanContext, Tracer }
import io.opentracing.propagation.Format

package object syntax {

  final implicit class TracedOps[F[_], A](fa: F[A])(implicit traced: Traced[F]) {
    def tracing(operation: String, tags: Traced.Tag*): F[A] = traced(operation, tags: _*)(fa)

    def injecting(context: SpanContext)(operation: String, tags: Traced.Tag*): F[A] =
      traced.injectContext(context)(operation, tags: _*)(fa)

    def injectingFrom[C](carrier: C, format: Format[C])(operation: String, tags: Traced.Tag*): F[A] =
      traced.injectContextFrom(carrier, format)(operation, tags: _*)(fa)
  }

  sealed trait TracedFunctions {
    def currentSpan[F[_]](implicit traced: Traced[F]): F[Traced.SpanInterface[F]] = traced.currentSpan

    def extractContext[F[_]](implicit traced: Traced[F]): F[Traced.SpanInterface[F]] = traced.currentSpan

    def trace[F[_]](operation: String, tags: Traced.Tag*): TracedFunctions.Trace[F] = new TracedFunctions.Trace(operation, tags)

    def delay[F[_]]: TracedFunctions.Delay[F] = TracedFunctions.delayInstance.asInstanceOf[TracedFunctions.Delay[F]]
  }

  object TracedFunctions extends TracedFunctions {
    protected[syntax] class Trace[F[_]](operation: String, tags: Seq[Traced.Tag]) {
      def apply[A](a: => A)(implicit traced: Traced[F]): F[A] = traced(operation, tags: _*)(traced.defer(traced.pure(a)))
    }
    protected[syntax] class Delay[F[_]] {
      def apply[A](a: => A)(implicit traced: Traced[F]): F[A] = traced.defer(traced.pure(a))
    }
    protected lazy val delayInstance = new TracedFunctions.Delay[cats.Id]
  }

  final implicit class TracedIdOps(obj: Traced.type) extends TracedFunctions

  final implicit class TracedRunOps[F[_[*], *], G[_], A](fa: F[G, A])(implicit tracedRun: TracedRun[F, G]) {
    def runTraced(tracer: Tracer, hooks: Traced.Hooks[G]): G[A] = tracedRun(fa, tracer, hooks)
    def runTraced(tracer: Tracer)(implicit A: Applicative[G]): G[A] = tracedRun(fa, tracer, Traced.Hooks[G]())
  }
}
