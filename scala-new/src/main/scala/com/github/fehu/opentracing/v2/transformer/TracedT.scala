package com.github.fehu.opentracing.v2.transformer

import cats.data.StateT
import cats.{ Applicative, FlatMap, Functor, ~> }
import cats.syntax.flatMap._
import cats.syntax.functor._
import io.opentracing.{ Span, Tracer }

import com.github.fehu.opentracing.v2.Traced
import com.github.fehu.opentracing.v2.internal.State

object TracedT {
  def liftK[F[_]: Applicative]: F ~> TracedT[F, *] = StateT.liftK

  def runK[F[_]: FlatMap](params: RunParams[F]): TracedT[F, *] ~> F =
    λ[TracedT[F, *] ~> F](t => toState(params).flatMap(t.run(_).map(_._2)))

  final case class RunParams[F[_]](tracer: Tracer, hooks: Traced.Hooks[F], activeSpan: F[Option[Span]])

  private[opentracing] def toState[F[_]: Functor](params: RunParams[F]) =
    params.activeSpan.map(State(params.tracer, params.hooks, _))
}
