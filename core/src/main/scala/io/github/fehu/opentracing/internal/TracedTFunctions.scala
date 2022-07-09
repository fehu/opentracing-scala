package io.github.fehu.opentracing.internal

import cats.data.StateT
import cats.effect.{ IO, LiftIO, Sync }
import cats.syntax.functor.*
import cats.{ Applicative, ApplicativeError, Functor, ~> }

import io.github.fehu.opentracing.Traced
import io.github.fehu.opentracing.internal.compat.FK
import io.github.fehu.opentracing.propagation.Propagation
import io.github.fehu.opentracing.transformer.TracedT
import io.github.fehu.opentracing.transformer.TracedT.AutoConvert.*

private[opentracing] trait TracedTFunctions {
  def pure[F[_]: Applicative, A](a: A): TracedT[F, A] = TracedT(StateT.pure(a))

  def liftF[F[_]: Applicative, A](fa: F[A]): TracedT[F, A] = TracedT(StateT.liftF(fa))
  def liftIO[F[_]: Applicative: LiftIO, A](io: IO[A]): TracedT[F, A] = TracedT(StateT.liftF(LiftIO[F].liftIO(io)))

  def raiseError[F[_], A](err: Throwable)(implicit A: ApplicativeError[F, Throwable]): TracedT[F, A] = liftF(A.raiseError[A](err))

  def defer[F[_]: Sync, A](tfa: => TracedT[F, A]): TracedT[F, A] = traced.defer(tfa)
  def deferIO[F[_]: Sync: LiftIO, A](io: => IO[A]): TracedT[F, A] = defer(liftIO(io))
  def delay[F[_]: Sync, A](a: => A): TracedT[F, A] = defer(pure(a))

  def currentSpan[F[_]: Sync]: Traced.SpanInterface[TracedT[F, _]] = traced.currentSpan
  def extractContext[F[_]: Sync, C <: Propagation#Carrier](carrier: C): TracedT[F, Option[C]] = traced.extractContext(carrier)

  def liftK[F[_]: Applicative]: F ~> TracedT[F, _] = FK.lift[F, TracedT[F, _]](liftF)

  def mapK[F[_]: Functor, G[_]](fk: F ~> G): TracedT[F, _] ~> TracedT[G, _] =
    FK.lift[TracedT[F, _], TracedT[G, _]](_.stateT.mapK(fk))

  def runK[F[_]: Sync](params: Traced.RunParams): TracedT[F, _] ~> F =
    FK.lift[TracedT[F, _], F](_.stateT.run(State.fromRunParams(params)).map(_._2))

  def traceK[F[_]: Sync](operation: String, tags: Traced.Tag*): F ~> TracedT[F, _] =
    FK.lift[F, TracedT[F, _]](fa => traced.apply(operation, tags*)(liftF(fa)))

  private def traced[F[_]: Sync]: Traced[TracedT[F, _]] = TracedT.tracedTTracedInstance[F]
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

  def currentSpan: Traced.SpanInterface[TracedT[F, _]] = traced.currentSpan
  def extractContext[C <: Propagation#Carrier](carrier: C): TracedT[F, Option[C]] = traced.extractContext(carrier)

  def liftK: F ~> TracedT[F, _] = FK.lift[F, TracedT[F, _]](liftF)
  def mapK[G[_]](fk: F ~> G): TracedT[F, _] ~> TracedT[G, _] =
    FK.lift[TracedT[F, _], TracedT[G, _]](_.stateT.mapK(fk))

  def runK(params: Traced.RunParams): TracedT[F, _] ~> F = TracedT.runK(params)
  def traceK(operation: String, tags: Traced.Tag*): F ~> TracedT[F, _] =
    FK.lift[F, TracedT[F, _]](fa => traced(operation, tags*)(liftF(fa)))

  implicit lazy val traced: Traced[TracedT[F, _]] = TracedT.tracedTTracedInstance[F]
}
