package com.github.fehu.opentracing.v2.internal

import cats.data.StateT
import cats.effect.{ CancelToken, ConcurrentEffect, ExitCase, Fiber, IO, Sync, SyncIO }
import cats.effect.syntax.bracket._
import cats.instances.option._
import cats.syntax.apply._
import cats.syntax.either._
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.syntax.traverse._
import cats.~>
import io.opentracing.propagation.Format
import io.opentracing.{ Span, SpanContext, Tracer }

import com.github.fehu.opentracing.v2.{ Traced, Traced2 }
import com.github.fehu.opentracing.v2.transformer.TracedT

private[opentracing] trait TracedTTracedInstances extends TracedTTracedLowPriorityInstances {
  implicit def tracedStateTracedInstance[F[_]: Sync]: Traced2[TracedT, F] = new TracedTTracedInstance

  implicit def tracedTConcurrentEffectInstance[F[_]: ConcurrentEffect: TracedT.RunParams]: ConcurrentEffect[TracedT[F, *]] =
    new TracedTConcurrentEffectInstance[F]
}

private[opentracing] trait TracedTTracedLowPriorityInstances {
  implicit def tracedTSyncInstance[F[_]: Sync]: Sync[TracedT[F, *]] = new TracedTSyncInstance[F]
}

private[opentracing] class TracedTTracedInstance[F[_]](implicit sync: Sync[F]) extends Traced2[TracedT, F] { self =>
  private def state = StateT.get[F, State[F]]
  private def setState = StateT.set[F, State[F]] _

  def pure[A](a: A): TracedT[F, A] = StateT.pure(a)

  def defer[A](fa: => TracedT[F, A]): TracedT[F, A] = StateT.liftF(sync.delay(fa)).flatMap(locally)

  def lift[A](fa: F[A]): TracedT[F, A] = StateT.liftF(fa)

  def currentSpan: Traced.SpanInterface[TracedT[F, *]] = new CurrentSpan[TracedT[F, *]](state.map(_.currentSpan))

  def apply[A](op: String, tags: Traced.Tag*)(fa: TracedT[F, A]): TracedT[F, A] =
    for {
      s    <- state
      span <- StateT liftF Tools.newSpan(s.tracer, s.currentSpan.map(Left(_)), s.hooks.beforeStart, op, tags)
      a    <- execWithSpan(s, span, fa)
    } yield a

  private def execWithSpan[A](state: State[F], span: Span, fa: TracedT[F, A]) = {
    val spanInterface = CurrentSpan(span)
    for {
      _ <- StateT liftF state.hooks.justAfterStart(spanInterface)
      _ <- setState(state.copy(currentSpan = Some(span)))
      fin = (e: Option[Throwable]) => state.hooks.beforeStop(spanInterface)(e)
                                           .guarantee(sync.delay{ span.finish() })
      a <- fa.transformF(_.guaranteeCase {
             case ExitCase.Completed => fin(None)
             case ExitCase.Canceled  => fin(Some(new Exception("Canceled")))
             case ExitCase.Error(e)  => fin(Some(e))
           })
    } yield a
  }

  def injectContext(context: SpanContext): Traced.Interface[TracedT[F, *]] = new Interface(StateT.pure(context))

  def injectContextFrom[C](carrier: C, format: Format[C]): Traced.Interface[TracedT[F, *]] =
    new Interface(
      for {
        s <- state
        c <- StateT liftF sync.delay{ s.tracer.extract(format, carrier) }
      } yield c
    )

  private class Interface(context: TracedT[F, SpanContext]) extends Traced.Interface[TracedT[F, *]] {
    def apply[A](op: String, tags: Traced.Tag*)(fa: TracedT[F, A]): TracedT[F, A] =
      for {
        s    <- state
        c    <- context
        span <- StateT liftF Tools.newSpan(s.tracer, Option(c).map(Right(_)), s.hooks.beforeStart, op, tags)
        a    <- execWithSpan(s, span, fa)
      } yield a
  }

  def extractContext[C](carrier: C, format: Format[C]): TracedT[F, Option[C]] =
    for {
      s <- state
      o <- StateT liftF s.currentSpan.traverse(span => sync.delay(s.tracer.inject(span.context(), format, carrier)))
    } yield o.map(_ => carrier)

  def run[A](traced: TracedT[F, A], tracer: Tracer, hooks: Traced.Hooks[F], parent: Option[Span]): F[A] =
    traced.run(State[F](tracer, hooks, parent)).map(_._2)
}

private[opentracing] class TracedTSyncInstance[F[_]](implicit sync: Sync[F]) extends Sync[TracedT[F, *]] {
  def suspend[A](thunk: => TracedT[F, A]): TracedT[F, A] = StateT.liftF(sync.delay(thunk)).flatMap(locally)

  def bracketCase[A, B](acquire: TracedT[F, A])
                       (use: A => TracedT[F, B])
                       (release: (A, ExitCase[Throwable]) => TracedT[F, Unit]): TracedT[F, B] =
    for {
      s0      <- StateT.get[F, State[F]]
      (s1, b) <- StateT liftF sync.bracketCase(
                  acquire.run(s0)
                 ){
                   case (s, a) => use(a).run(s)
                 } {
                   case ((s, a), e) => release(a, e).run(s).void
                 }
      _       <- StateT.set[F, State[F]](s1)
    } yield b

  def pure[A](x: A): TracedT[F, A] = StateT.pure(x)

  override def map[A, B](fa: TracedT[F, A])(f: A => B): TracedT[F, B] = fa.map(f)

  def flatMap[A, B](fa: TracedT[F, A])(f: A => TracedT[F, B]): TracedT[F, B] = fa.flatMap(f)

  // TODO ===============================================================================================================================================
  // TODO ===============================================================================================================================================
  // TODO ===============================================================================================================================================
  def tailRecM[A, B](a: A)(f: A => TracedT[F, Either[A, B]]): TracedT[F, B] = ???

  def raiseError[A](e: Throwable): TracedT[F, A] = StateT.liftF(sync.raiseError(e))

  def handleErrorWith[A](fa: TracedT[F, A])(f: Throwable => TracedT[F, A]): TracedT[F, A] =
    for {
      s0      <- StateT.get[F, State[F]]
      (s1, a) <- StateT liftF sync.handleErrorWith(fa.run(s0))(f andThen (_.run(s0)))
      _       <- StateT.set[F, State[F]](s1)
    } yield a
}

class TracedTConcurrentEffectInstance[F[_]](
  implicit
  ce: ConcurrentEffect[F],
  params: TracedT.RunParams[F]
) extends TracedTSyncInstance[F] with ConcurrentEffect[TracedT[F, *]] {

  private def state = StateT.get[F, State[F]]
  private def setState = StateT.set[F, State[F]] _

  private def runP[A](traced: TracedT[F, A]) =
    params.activeSpan.flatMap{ opt => traced.run(State(params.tracer, params.hooks, opt)) }.map(_._2)

  private def run[A](s: State[F])(traced: TracedT[F, A]) = traced.run(s).map(_._2)
  private def runVoid[A](s: State[F])(traced: TracedT[F, A]) = traced.run(s).void

  def async[A](k: (Either[Throwable, A] => Unit) => Unit): TracedT[F, A] = StateT liftF ce.async(k)

  def asyncF[A](k: (Either[Throwable, A] => Unit) => TracedT[F, Unit]): TracedT[F, A] =
    for {
      s0 <- state
      a  <- StateT liftF ce.asyncF[A](k andThen runVoid(s0))
    } yield a

  def runAsync[A](fa: TracedT[F, A])(cb: Either[Throwable, A] => IO[Unit]): SyncIO[Unit] =
    ce.runAsync[A](runP(fa))(cb)

  def runCancelable[A](fa: TracedT[F, A])(cb: Either[Throwable, A] => IO[Unit]): SyncIO[CancelToken[TracedT[F, *]]] =
    ce.runCancelable(runP(fa))(cb).map(StateT.liftF[F, State[F], Unit])

  def start[A](fa: TracedT[F, A]): TracedT[F, Fiber[TracedT[F, *], A]] =
    for {
      s0 <- state
      f  <- StateT liftF ce.start(run(s0)(fa))
    } yield f.mapK(StateT.liftK[F, State[F]] andThen λ[TracedT[F, *] ~> TracedT[F, *]](_ <* setState(s0)))
  // TODO

  def racePair[A, B](fa: TracedT[F, A], fb: TracedT[F, B]): TracedT[F, Either[(A, Fiber[TracedT[F, *], B]), (Fiber[TracedT[F, *], A], B)]] =
    for {
      s0 <- state
      ef <- StateT liftF ce.racePair(run(s0)(fa), run(s0)(fb))
    } yield ef.leftMap{ case (a, f) => a -> f.mapK(StateT.liftK[F, State[F]]) }
                 .map { case (f, b) => f.mapK(StateT.liftK[F, State[F]]) -> b }

}
