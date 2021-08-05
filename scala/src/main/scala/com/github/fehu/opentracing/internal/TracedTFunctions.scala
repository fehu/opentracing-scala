package com.github.fehu.opentracing.internal

import cats.data.StateT
import cats.effect.{ IO, LiftIO, Sync }
import cats.syntax.functor._
import cats.{ Applicative, ApplicativeError, Functor, ~> }
import io.opentracing.propagation.Format

import com.github.fehu.opentracing.Traced
import com.github.fehu.opentracing.transformer.TracedT
import com.github.fehu.opentracing.transformer.TracedT.AutoConvert._

private[opentracing] trait TracedTFunctions {
  def pure[F[_]: Applicative, A](a: A): TracedT[F, A] = TracedT(StateT.pure(a))

  def liftF[F[_]: Applicative, A](fa: F[A]): TracedT[F, A] = TracedT(StateT.liftF(fa))
  def liftIO[F[_]: Applicative: LiftIO, A](io: IO[A]): TracedT[F, A] = TracedT(StateT.liftF(LiftIO[F].liftIO(io)))

  def raiseError[F[_], A](err: Throwable)(implicit A: ApplicativeError[F, Throwable]): TracedT[F, A] = liftF(A.raiseError[A](err))

  def defer[F[_]: Sync, A](tfa: => TracedT[F, A]): TracedT[F, A] = traced.defer(tfa)
  def deferIO[F[_]: Sync: LiftIO, A](io: => IO[A]): TracedT[F, A] = defer(liftIO(io))
  def delay[F[_]: Sync, A](a: => A): TracedT[F, A] = defer(pure(a))

  def currentSpan[F[_]: Sync]: Traced.SpanInterface[TracedT[F, *]] = traced.currentSpan
  def extractContext[F[_]: Sync, C0 <: C, C](carrier: C0, format: Format[C]): TracedT[F, Option[C0]] =
    traced.extractContext(carrier, format)

  def liftK[F[_]: Applicative]: F ~> TracedT[F, *] = λ[F ~> TracedT[F, *]](liftF(_))

  def mapK[F[_]: Functor, G[_]](fk: F ~> G): TracedT[F, *] ~> TracedT[G, *] = λ[TracedT[F, *] ~> TracedT[G, *]](_.stateT.mapK(fk))

  def runK[F[_]: Sync](params: Traced.RunParams): TracedT[F, *] ~> F = λ[TracedT[F, *] ~> F](_.stateT.run(toState(params)).map(_._2))

  def traceK[F[_]: Sync](operation: String, tags: Traced.Tag*): F ~> TracedT[F, *] =
    λ[F ~> TracedT[F, *]](fa => traced.apply(operation, tags: _*)(liftF(fa)))

  private def traced[F[_]: Sync]: Traced[TracedT[F, *]] = TracedT.tracedTTracedInstance[F]

  private def toState[F[_]: Functor](params: Traced.RunParams) =
    State(params.tracer, params.hooks, params.activeSpan.maybe, params.logError)
}

abstract class TracedTFunctionsForSync[F[_]: Sync] {
  def pure[A](a: A): TracedT[F, A] = TracedT(StateT.pure(a))
  lazy val unit: TracedT[F, Unit] = pure(())

  def liftF[A](fa: F[A]): TracedT[F, A] = TracedT(StateT.liftF(fa))
  def liftIO[A](io: IO[A])(implicit lift: LiftIO[F]): TracedT[F, A] = TracedT(StateT.liftF(lift.liftIO(io)))

  def raiseError[A](err: Throwable): TracedT[F, A] = liftF(Sync[F].raiseError[A](err))

  def defer[A](tfa: => TracedT[F, A]): TracedT[F, A] = traced.defer(tfa)
  def deferIO[A](io: => IO[A])(implicit lift: LiftIO[F]): TracedT[F, A] = defer(liftIO(io))
  def delay[A](a: => A): TracedT[F, A] = defer(pure(a))

  def currentSpan: Traced.SpanInterface[TracedT[F, *]] = traced.currentSpan
  def extractContext[C0 <: C, C](carrier: C0, format: Format[C]): TracedT[F, Option[C0]] =
    traced.extractContext(carrier, format)

  def liftK: F ~> TracedT[F, *] = λ[F ~> TracedT[F, *]](liftF(_))
  def mapK[G[_]](fk: F ~> G): TracedT[F, *] ~> TracedT[G, *] = λ[TracedT[F, *] ~> TracedT[G, *]](_.stateT.mapK(fk))

  def runK(params: Traced.RunParams): TracedT[F, *] ~> F = TracedT.runK(params)
  def traceK(operation: String, tags: Traced.Tag*): F ~> TracedT[F, *] =
    λ[F ~> TracedT[F, *]](fa => traced(operation, tags: _*)(liftF(fa)))

  implicit lazy val traced: Traced[TracedT[F, *]] = TracedT.tracedTTracedInstance[F]
}
