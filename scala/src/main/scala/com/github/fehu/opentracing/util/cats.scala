package com.github.fehu.opentracing.util

import scala.language.higherKinds

import _root_.cats.{ Applicative, Defer, Id }

object cats {

  def defer[F[_]]: DeferOps[F] = DeferOps.asInstanceOf[DeferOps[F]]

  final class DeferOps[F[_]] {
    def apply[A](a: => A)(implicit D: Defer[F], A: Applicative[F]): F[A] = D.defer(A.pure(a))
  }
  private lazy val DeferOps = new DeferOps[Id]

}
