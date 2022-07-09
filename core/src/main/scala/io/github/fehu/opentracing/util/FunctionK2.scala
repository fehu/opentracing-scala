package io.github.fehu.opentracing.util

trait FunctionK2[F[_[_]], G[_[_]]] extends Serializable { self =>
  def apply[A[_]](fa: F[A]): G[A]

  def compose[E[_[_]]](f: FunctionK2[E, F]): FunctionK2[E, G] =
    new FunctionK2[E, G] {
      def apply[A[_]](fa: E[A]): G[A] = self(f(fa))
    }

  def andThen[H[_[_]]](f: FunctionK2[G, H]): FunctionK2[F, H] = f.compose(self)
}

object FunctionK2 {
  type ~~>[F[_[_]], G[_[_]]] = FunctionK2[F, G]
}
