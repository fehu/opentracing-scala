package com.github.fehu.opentracing.v2.internal.syntax

import io.opentracing.SpanContext
import io.opentracing.propagation.Format

import com.github.fehu.opentracing.v2.{ Traced, Traced2 }

protected[opentracing] trait LowPrioritySyntax {

  final implicit class Traced2WrapOps[G[_], A](ga: G[A]) {
    def trace[F[_[*], *]](operation: String, tags: Traced.Tag*)(implicit traced: Traced2[F, G]): F[G, A] =
      traced(operation, tags: _*)(traced.lift(ga))

    def inject[F[_[*], *]](context: SpanContext)(operation: String, tags: Traced.Tag*)(implicit traced: Traced2[F, G]): F[G, A] =
      traced.injectContext(context)(operation, tags: _*)(traced.lift(ga))

    def inject[F[_[*], *]](context: Option[SpanContext])(operation: String, tags: Traced.Tag*)(implicit traced: Traced2[F, G]): F[G, A] =
      context.map(inject(_)(operation, tags: _*)).getOrElse(traced.lift(ga))

    def injectFrom[F[_[*], *], C](format: Format[C])(carrier: C)(operation: String, tags: Traced.Tag*)(implicit traced: Traced2[F, G]): F[G, A] =
      traced.injectContextFrom(format)(carrier)(operation, tags: _*)(traced.lift(ga))

    def injectFrom[F[_[*], *], C](carrier: Option[C], format: Format[C])(operation: String, tags: Traced.Tag*)(implicit traced: Traced2[F, G]): F[G, A] =
      carrier.map(injectFrom(format)(_)(operation, tags: _*)).getOrElse(traced.lift(ga))

  }

}
