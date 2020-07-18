package com.github.fehu.opentracing.v2

import scala.language.existentials

import cats.arrow.FunctionK
import cats.{ Applicative, Defer, Monad, ~> }
import cats.effect.Resource
import cats.syntax.apply._
import cats.syntax.flatMap._
import io.opentracing.{ Span, SpanContext, Tracer }
import io.opentracing.propagation.Format

import com.github.fehu.opentracing.v2.Traced.SpanInterface

package object syntax {

  final implicit class TracedOps[F[_], A](fa: F[A])(implicit traced: Traced[F]) {
    def trace(operation: String, tags: Traced.Tag*): F[A] = traced(operation, tags: _*)(fa)

    def inject(context: SpanContext)(operation: String, tags: Traced.Tag*): F[A] =
      traced.injectContext(context)(operation, tags: _*)(fa)

    def injectFrom[C](carrier: C, format: Format[C])(operation: String, tags: Traced.Tag*): F[A] =
      traced.injectContextFrom(carrier, format)(operation, tags: _*)(fa)
  }

  sealed trait TracedFunctions {
    def currentSpan[F[_]](implicit traced: Traced[F]): Traced.SpanInterface[F] = traced.currentSpan

    def extractContext[F[_]]: TracedFunctions.Extract[F] = TracedFunctions.extractInstance.asInstanceOf[TracedFunctions.Extract[F]]

    def trace[F[_]](operation: String, tags: Traced.Tag*): TracedFunctions.Trace[F] = new TracedFunctions.Trace(operation, tags)

    def traceK[F[_]](operation: String, tags: Traced.Tag*)(implicit traced: Traced[F]): F ~> F =
      λ[F ~> F](f => traced(operation, tags: _*)(f))

    def delay[F[_]]: TracedFunctions.Delay[F] = TracedFunctions.delayInstance.asInstanceOf[TracedFunctions.Delay[F]]
  }

  object TracedFunctions extends TracedFunctions {
    protected[syntax] class Extract[F[_]] {
      def apply[C0 <: C, C](carrier: C0, format: Format[C])(implicit traced: Traced[F]): F[Option[C0]] =
        traced.extractContext(carrier, format)
    }
    protected[syntax] class Trace[F[_]](operation: String, tags: Seq[Traced.Tag]) {
      def apply[A](a: => A)(implicit traced: Traced[F]): F[A] = traced(operation, tags: _*)(traced.defer(traced.pure(a)))
    }
    protected[syntax] class Delay[F[_]] {
      def apply[A](a: => A)(implicit traced: Traced[F]): F[A] = traced.defer(traced.pure(a))
    }
    protected lazy val extractInstance = new TracedFunctions.Extract[cats.Id]
    protected lazy val delayInstance = new TracedFunctions.Delay[cats.Id]
  }

  final implicit class TracedIdOps(obj: Traced.type) extends TracedFunctions

  final implicit class Traced2Ops[F[_[*], *], G[_], A](fa: F[G, A])(implicit traced: Traced2[F, G]) {
    def runTraced(tracer: Tracer, hooks: Traced.Hooks[G], parent: Span): G[A] =
      traced.run(fa, tracer, hooks, Option(parent))
    def runTraced(tracer: Tracer, hooks: Traced.Hooks[G]): G[A] =
      traced.run(fa, tracer, hooks, None)
    def runTraced(tracer: Tracer, parent: Span)(implicit A: Applicative[G]): G[A] =
      traced.run(fa, tracer, Traced.Hooks[G](), Option(parent))
    def runTraced(tracer: Tracer)(implicit A: Applicative[G]): G[A] =
      traced.run(fa, tracer, Traced.Hooks[G](), None)
  }

  final implicit class TracedResourceOps[F[_]: Monad: Defer, A](resource: Resource[F, A])
                                                               (implicit traced: Traced[F]) {
    def traceLifetime(operation: String, tags: Traced.Tag*): Resource[F, A] =
      Resource.liftF(traced(operation, tags: _*)(traced.pure(resource))).flatMap(locally)

    def traceUsage(operation: String, tags: Traced.Tag*): Resource[F, A] =
      resource.mapK(TracedFunctions.traceK(operation, tags: _*))

    def traceUsageK(operation: String, tags: Traced.Tag*)(f: F ~> F): Resource[F, A] =
      resource.mapK(TracedFunctions.traceK(operation, tags: _*) andThen f)

    def traceUsageF(operation: String, tags: Traced.Tag*)(f: (Any, SpanInterface[F]) => F[Unit]): Resource[F, A] = {
      def f1[X](p: (X, SpanInterface[F])): F[Unit] = f.tupled.asInstanceOf[((X, SpanInterface[F])) => F[Unit]].apply(p)
      traceUsageFK(operation, tags: _*)(FunctionK.lift[λ[X => (X, SpanInterface[F])], λ[X => F[Unit]]](f1))
    }

    def traceUsageFK(operation: String, tags: Traced.Tag*)(f: λ[X => (X, SpanInterface[F])] ~> λ[X => F[Unit]]): Resource[F, A] =
      traceUsageK(operation, tags: _*)(
        λ[F ~> F](fa => fa.flatTap(a => f(a, traced.currentSpan) *> traced.pure(a)))
      )
  }

}
