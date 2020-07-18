package com.github.fehu.opentracing.v2.util

trait FunctionK2[F[*[_]], G[*[_]]] extends Serializable { self =>
  def apply[A[_]](fa: F[A]): G[A]

  def compose[E[*[_]]](f: FunctionK2[E, F]): FunctionK2[E, G] =
    new FunctionK2[E, G] {
      def apply[A[_]](fa: E[A]): G[A] = self(f(fa))
    }

  def andThen[H[*[_]]](f: FunctionK2[G, H]): FunctionK2[F, H] = f.compose(self)
}

object FunctionK2 {
  type ~~>[F[*[_]], G[*[_]]] = FunctionK2[F, G]
}
