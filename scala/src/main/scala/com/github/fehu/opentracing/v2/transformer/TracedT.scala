package com.github.fehu.opentracing.v2.transformer

import cats.data.StateT
import cats.effect.{ Effect, IO, LiftIO }
import cats.effect.syntax.effect._
import cats.{ Applicative, FlatMap, Functor, ~> }
import cats.syntax.functor._
import io.opentracing.propagation.Format

import com.github.fehu.opentracing.v2.Traced
import com.github.fehu.opentracing.v2.internal.{ State, TracedTTracedInstance }

object TracedT {
  def liftK[F[_]: Applicative]: F ~> TracedT[F, *] = StateT.liftK

  def runK[F[_]: FlatMap](params: Traced.RunParams): TracedT[F, *] ~> F =
    λ[TracedT[F, *] ~> F](_.run(toState(params)).map(_._2))

  private[opentracing] def toState[F[_]: Functor](params: Traced.RunParams) =
    State(params.tracer, params.hooks, params.activeSpan.maybe)
}

object TracedTIO {
  def pure[A](a: A): TracedTIO[A] = StateT.pure(a)
  lazy val unit: TracedTIO[Unit] = pure(())

  def liftF[F[_]: Effect, A](fa: F[A]): TracedTIO[A] = StateT.liftF(fa.toIO)
  def liftIO[A](io: IO[A]): TracedTIO[A] = StateT.liftF(io)

  def raiseError[A](err: Throwable): TracedTIO[A] = liftF(IO.raiseError[A](err))

  def defer[A](tio: => TracedTIO[A]): TracedTIO[A] = tracedTIO.defer(tio)
  def deferIO[A](io: => IO[A]): TracedTIO[A] = defer(liftIO(io))
  def delay[A](a: => A): TracedTIO[A] = defer(pure(a))

  def currentSpan: Traced.SpanInterface[TracedTIO] = tracedTIO.currentSpan
  def extractContext[C0 <: C, C](carrier: C0, format: Format[C]): TracedTIO[Option[C0]] =
    tracedTIO.extractContext(carrier, format)

  def liftK[F[_]: Effect]: F ~> TracedTIO = λ[F ~> TracedTIO](liftF(_))
  def mapK[F[_]: LiftIO]: TracedTIO ~> TracedT[F, *] = λ[TracedTIO ~> TracedT[F, *]](_.mapK(LiftIO.liftK))
  def comapK[F[_]: Effect]: TracedT[F, *] ~> TracedTIO = λ[TracedT[F, *] ~> TracedTIO](_.mapK(Effect.toIOK))

  def runK(params: Traced.RunParams): TracedTIO ~> IO = TracedT.runK(params)
  def traceK(operation: String, tags: Traced.Tag*): IO ~> TracedTIO =
    λ[IO ~> TracedTIO](io => tracedTIO(operation, tags: _*)(liftF(io)))

  private lazy val tracedTIO = new TracedTTracedInstance[IO]
}
