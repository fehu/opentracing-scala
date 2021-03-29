package com.github.fehu.opentracing.propagation

import cats.effect.Sync
import cats.syntax.flatMap._
import cats.syntax.functor._

import com.github.fehu.opentracing.Traced
import com.github.fehu.opentracing.syntax.TracedFunctions

package object syntax {

  final implicit class TracedPropagationOps[F[_]: Traced, A](fa: F[A]) {
    def injectPropagated[C <: Propagation](carrier: C)(operation: String, tags: Traced.Tag*): F[A] =
      Traced[F].injectContextFrom(carrier.format)(carrier.underlying)(operation, tags: _*)(fa)

    def injectPropagatedOpt[C <: Propagation](carrier: Option[C])(operation: String, tags: Traced.Tag*): F[A] =
      carrier.map(c => injectPropagated(c)(operation, tags: _*)).getOrElse(fa)
  }

  final implicit class TracedPropagationExtractOps[F[_]: Traced: Sync](e: TracedFunctions.Extract[F]) {
    def to[C <: Propagation](implicit companion: PropagationCompanion[C]): F[Option[C]] =
      for {
        carrier <- Sync[F].delay { companion() }
        uOpt    <- Traced[F].extractContext(carrier.underlying, companion.format)
      } yield uOpt.as(carrier)
  }
}
