package com.github.fehu.opentracing.v2.internal.syntax

import com.github.fehu.opentracing.v2.{ Traced, Traced2 }

protected[opentracing] trait LowPrioritySyntax {

  final implicit class Traced2WrapOps[G[_], A](ga: G[A]) {
    def trace[F[_[*], *]](operation: String, tags: Traced.Tag*)(implicit traced: Traced2[F, G]): F[G, A] =
      traced(operation, tags: _*)(traced.lift(ga))
  }

}
