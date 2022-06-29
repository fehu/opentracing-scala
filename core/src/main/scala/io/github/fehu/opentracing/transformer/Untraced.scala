package io.github.fehu.opentracing.transformer

import cats.{Applicative, Defer}

import io.github.fehu.opentracing.Traced
import io.github.fehu.opentracing.internal.TracedStub

object Untraced {
  /** Get a stub [[Traced]] instance for `F[_]`. */
  def tracedStub[F[_]: Applicative: Defer]: Traced[F] = new TracedStub
}
