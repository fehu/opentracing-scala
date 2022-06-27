package com.github.fehu.opentracing.transformer

import scala.concurrent.Future

import cats.{ FlatMap, Functor, ~> }
import cats.data.StateT
import cats.effect.{ Async, IO, LiftIO, Resource }
import cats.effect.std.Dispatcher

import com.github.fehu.opentracing.Traced
import com.github.fehu.opentracing.internal.{ State, TracedTFunctions, TracedTFunctionsForSync, TracedTInstances }
import com.github.fehu.opentracing.internal.compat.FK

final case class TracedT[F[_], A](stateT: StateT[F, State, A]) extends AnyVal {
  def transform[G[_], B](f: StateT[F, State, A] => StateT[G, State, B]): TracedT[G, B] = copy(f(stateT))

  def map[B](f: A => B)(implicit F: Functor[F]): TracedT[F, B] = transform(_.map(f))
  def flatMap[B](f: A => TracedT[F, B])(implicit F: FlatMap[F]): TracedT[F, B] = transform(_.flatMap(f andThen (_.stateT)))
  def flatMapF[B](f: A => F[B])(implicit F: FlatMap[F]): TracedT[F, B] = transform(_.flatMapF(f))

  def mapK[G[_]](fk: F ~> G)(implicit F: Functor[F]): TracedT[G, A] = transform(_.mapK(fk))
}

object TracedT extends TracedTInstances with TracedTFunctions {
  type Underlying[F[_], A] = StateT[F, State, A]

  def dispatcher[F[_]: FlatMap](d: Dispatcher[F], params: Traced.RunParams): Dispatcher[TracedT[F, _]] =
    new TracedTDispatcher[F](d, State.fromRunParams(params))

  def dispatcher[F[_]: Async](params: Traced.RunParams): Resource[F, Dispatcher[TracedT[F, _]]] =
    Dispatcher[F].map(new TracedTDispatcher[F](_, State.fromRunParams(params)))

  private[opentracing] object AutoConvert {
    import scala.language.implicitConversions

    @inline implicit def autoToStateT[F[_], A](tt: TracedT[F, A]): Underlying[F, A] = tt.stateT
    @inline implicit def autoFromStateT[F[_], A](st: Underlying[F, A]): TracedT[F, A] = new TracedT(st)
  }
  private [opentracing] def toK[F[_]]: StateT[F, State, _] ~> TracedT[F, _] =
    FK.lift[StateT[F, State, _], TracedT[F, _]](TracedT.apply)

  private [opentracing] def fromK[F[_]]: TracedT[F, _] ~> StateT[F, State, _] =
    FK.lift[TracedT[F, _], StateT[F, State, _]](_.stateT)
}

object TracedIO extends TracedTFunctionsForSync[IO] {
  def mapIOK[F[_]: LiftIO]: TracedIO ~> TracedT[F, _] = mapK(LiftIO.liftK)

  def dispatcher(d: Dispatcher[IO], params: Traced.RunParams): Dispatcher[TracedIO] =
    new TracedTDispatcher[IO](d, State.fromRunParams(params))

  def dispatcher(params: Traced.RunParams): Resource[IO, Dispatcher[TracedT[IO, _]]] =
    Dispatcher[IO].map(new TracedTDispatcher[IO](_, State.fromRunParams(params)))
}

private[opentracing] class TracedTDispatcher[F[_]: FlatMap](df: Dispatcher[F], s0: State) extends Dispatcher[TracedT[F, _]] {
  def unsafeToFutureCancelable[A](fa: TracedT[F, A]): (Future[A], () => Future[Unit]) =
    df.unsafeToFutureCancelable(fa.stateT.runA(s0))
}
