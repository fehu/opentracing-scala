package io.github.fehu.opentracing.internal.compat

import cats.arrow.FunctionK

object FK {
  protected type τ[F[_], G[_]]

  @inline def lift[F[_], G[_]](f: F[τ[F, G]] => G[τ[F, G]]): FunctionK[F, G] =
    new FunctionK[F, G] {
      @inline def apply[A](fa: F[A]): G[A] = f(fa.asInstanceOf[F[τ[F, G]]]).asInstanceOf[G[A]]
    }
}
