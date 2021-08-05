package com.github.fehu.opentracing.transformer

import cats.~>
import cats.data.StateT
import cats.effect.{ Effect, IO, LiftIO }

import com.github.fehu.opentracing.internal.{ State, TracedTFunctions, TracedTFunctionsForSync, TracedTTracedInstances }

final case class TracedT[F[_], A](stateT: StateT[F, State, A]) extends AnyVal

object TracedT extends TracedTTracedInstances with TracedTFunctions {
  type Underlying[F[_], A] = StateT[F, State, A]

  private[opentracing] object AutoConvert {
    import scala.language.implicitConversions

    @inline implicit def autoToStateT[F[_], A](tt: TracedT[F, A]): Underlying[F, A] = tt.stateT
    @inline implicit def autoFromStateT[F[_], A](st: Underlying[F, A]): TracedT[F, A] = new TracedT(st)
  }
}

object TracedIO extends TracedTFunctionsForSync[IO] {
  def liftEffectK[F[_]: Effect]: F ~> TracedIO = liftK compose Effect.toIOK
  def mapIOK[F[_]: LiftIO]: TracedIO ~> TracedT[F, *] = mapK(LiftIO.liftK)
  def comapIOK[F[_]: Effect]: TracedT[F, *] ~> TracedIO = λ[TracedT[F, *] ~> TracedIO](t => TracedT(t.stateT.mapK(Effect.toIOK)))
}
