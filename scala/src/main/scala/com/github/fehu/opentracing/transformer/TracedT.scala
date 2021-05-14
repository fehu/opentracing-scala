package com.github.fehu.opentracing.transformer

import cats.data.StateT
import cats.effect.{ Effect, IO, LiftIO }
import cats.effect.syntax.effect._
import cats.{ Applicative, FlatMap, Functor, ~> }
import cats.syntax.functor._
import io.opentracing.propagation.Format

import com.github.fehu.opentracing.Traced
import com.github.fehu.opentracing.internal.{ State, TracedTTracedInstance }

object TracedT {
  def liftK[F[_]: Applicative]: F ~> TracedT[F, *] = StateT.liftK

  def runK[F[_]: FlatMap](params: Traced.RunParams): TracedT[F, *] ~> F =
    λ[TracedT[F, *] ~> F](_.run(toState(params)).map(_._2))

  private[opentracing] def toState[F[_]: Functor](params: Traced.RunParams) =
    State(params.tracer, params.hooks, params.activeSpan.maybe, params.logError)
}

object TracedIO {
  def pure[A](a: A): TracedIO[A] = StateT.pure(a)
  lazy val unit: TracedIO[Unit] = pure(())

  def liftF[F[_]: Effect, A](fa: F[A]): TracedIO[A] = StateT.liftF(fa.toIO)
  def liftIO[A](io: IO[A]): TracedIO[A] = StateT.liftF(io)

  def raiseError[A](err: Throwable): TracedIO[A] = liftF(IO.raiseError[A](err))

  def defer[A](tio: => TracedIO[A]): TracedIO[A] = tracedIO.defer(tio)
  def deferIO[A](io: => IO[A]): TracedIO[A] = defer(liftIO(io))
  def delay[A](a: => A): TracedIO[A] = defer(pure(a))

  def currentSpan: Traced.SpanInterface[TracedIO] = tracedIO.currentSpan
  def extractContext[C0 <: C, C](carrier: C0, format: Format[C]): TracedIO[Option[C0]] =
    tracedIO.extractContext(carrier, format)

  def liftK[F[_]: Effect]: F ~> TracedIO = λ[F ~> TracedIO](liftF(_))
  def mapK[F[_]: LiftIO]: TracedIO ~> TracedT[F, *] = λ[TracedIO ~> TracedT[F, *]](_.mapK(LiftIO.liftK))
  def comapK[F[_]: Effect]: TracedT[F, *] ~> TracedIO = λ[TracedT[F, *] ~> TracedIO](_.mapK(Effect.toIOK))

  def runK(params: Traced.RunParams): TracedIO ~> IO = TracedT.runK(params)
  def traceK(operation: String, tags: Traced.Tag*): IO ~> TracedIO =
    λ[IO ~> TracedIO](io => tracedIO(operation, tags: _*)(liftF(io)))

  private lazy val tracedIO = new TracedTTracedInstance[IO]
}
