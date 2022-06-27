package com.github.fehu.opentracing.internal.syntax

import io.opentracing.SpanContext

import com.github.fehu.opentracing.propagation.Propagation
import com.github.fehu.opentracing.{ Traced, Traced2 }

protected[opentracing] trait LowPrioritySyntax {

  final implicit class Traced2WrapOps[G[_], A](ga: G[A]) {
    def trace[F[_[_], _]](operation: String, tags: Traced.Tag*)(implicit traced: Traced2[F, G]): F[G, A] =
      traced(operation, tags*)(traced.lift(ga))

    def inject[F[_[_], _]](context: SpanContext)(operation: String, tags: Traced.Tag*)(implicit traced: Traced2[F, G]): F[G, A] =
      traced.injectContext(context)(operation, tags*)(traced.lift(ga))

    def inject[F[_[_], _]](context: Option[SpanContext])(operation: String, tags: Traced.Tag*)(implicit traced: Traced2[F, G]): F[G, A] =
      context.map(inject(_)(operation, tags*)).getOrElse(traced.lift(ga))

    def injectFrom[F[_[_], _]](carrier: Propagation#Carrier)(operation: String, tags: Traced.Tag*)(implicit traced: Traced2[F, G]): F[G, A] =
      traced.injectContextFrom(carrier)(operation, tags*)(traced.lift(ga))

    def injectFrom[F[_[_], _]](carrier: Option[Propagation#Carrier])(operation: String, tags: Traced.Tag*)(implicit traced: Traced2[F, G]): F[G, A] =
      carrier.map(injectFrom(_)(operation, tags*)).getOrElse(traced.lift(ga))

  }

}
