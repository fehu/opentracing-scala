package com.github.fehu.opentracing.v2.transformer

import cats.data.StateT
import cats.{ Applicative, FlatMap, Functor, ~> }
import cats.syntax.functor._

import com.github.fehu.opentracing.v2.Traced
import com.github.fehu.opentracing.v2.internal.State

object TracedT {
  def liftK[F[_]: Applicative]: F ~> TracedT[F, *] = StateT.liftK

  def runK[F[_]: FlatMap](params: Traced.RunParams): TracedT[F, *] ~> F =
    λ[TracedT[F, *] ~> F](_.run(toState(params)).map(_._2))

  private[opentracing] def toState[F[_]: Functor](params: Traced.RunParams) =
    State(params.tracer, params.hooks, params.activeSpan.maybe)
}
