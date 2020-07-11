package com.github.fehu.opentracing.internal

import cats.data.StateT
import cats.effect.{ ExitCase, Sync }
import cats.effect.syntax.bracket._
import cats.instances.option._
import cats.syntax.functor._
import cats.syntax.traverse._
import io.opentracing.propagation.Format
import io.opentracing.{ Span, SpanContext, Tracer }

import com.github.fehu.opentracing.{ Traced, TracedLift, TracedRun }
import com.github.fehu.opentracing.impl.TracedState

private[opentracing] class TracedStateTracedInstance[F[_]](implicit sync: Sync[F]) extends Traced[TracedState[F, *]] { self =>
  private def state = StateT.get[F, State[F]]
  private def setState = StateT.set[F, State[F]] _

  def pure[A](a: A): TracedState[F, A] = StateT.pure(a)

  def defer[A](fa: => TracedState[F, A]): TracedState[F, A] = StateT.liftF(sync.delay(fa)).flatMap(locally)

  def currentSpan: Traced.SpanInterface[TracedState[F, *]] = new CurrentSpan[TracedState[F, *]](state.map(_.currentSpan))

  def apply[A](op: String, tags: Traced.Tag*)(fa: TracedState[F, A]): TracedState[F, A] =
    for {
      s    <- state
      span <- StateT liftF Tools.newSpan(s.tracer, s.currentSpan.map(Left(_)), s.hooks.beforeStart, op, tags)
      a    <- execWithSpan(s, span, fa)
    } yield a

  private def execWithSpan[A](state: State[F], span: Span, fa: TracedState[F, A]) = {
    val spanInterface = CurrentSpan(span)
    for {
      _ <- StateT liftF state.hooks.justAfterStart(spanInterface)
      _ <- setState(state.copy(currentSpan = Some(span)))
      fin = (e: Option[Throwable]) => state.hooks.beforeStop(e)(spanInterface)
                                           .guarantee(sync.delay{ span.finish() })
      a <- fa.transformF(_.guaranteeCase {
             case ExitCase.Completed => fin(None)
             case ExitCase.Canceled  => fin(Some(new Exception("Canceled")))
             case ExitCase.Error(e)  => fin(Some(e))
           })
    } yield a
  }

  def injectContext(context: SpanContext): Traced.Interface[TracedState[F, *]] = new Interface(StateT.pure(context))

  def injectContextFrom[C](carrier: C, format: Format[C]): Traced.Interface[TracedState[F, *]] =
    new Interface(
      for {
        s <- state
        c <- StateT liftF sync.delay{ s.tracer.extract(format, carrier) }
      } yield c
    )

  private class Interface(context: TracedState[F, SpanContext]) extends Traced.Interface[TracedState[F, *]] {
    def apply[A](op: String, tags: Traced.Tag*)(fa: TracedState[F, A]): TracedState[F, A] =
      for {
        s    <- state
        c    <- context
        span <- StateT liftF Tools.newSpan(s.tracer, Option(c).map(Right(_)), s.hooks.beforeStart, op, tags)
        a    <- execWithSpan(s, span, fa)
      } yield a
  }

  def extractContext[C](carrier: C, format: Format[C]): TracedState[F, Option[C]] =
    for {
      s <- state
      o <- StateT liftF s.currentSpan.traverse(span => sync.delay(s.tracer.inject(span.context(), format, carrier)))
    } yield o.map(_ => carrier)

}

private[opentracing] class TracedStateTracedRunInstance[F[_]](implicit sync: Sync[F]) extends TracedRun[TracedState, F] {
  def apply[A](traced: TracedState[F, A], tracer: Tracer, hooks: Traced.Hooks[F], parent: Option[Span]): F[A] =
    traced.run(State[F](tracer, hooks, parent)).map(_._2)
}

private[opentracing] class TracedStateTracedLiftInstance[F[_]](implicit sync: Sync[F]) extends TracedLift[TracedState, F] {
  def apply[A](ga: F[A]): TracedState[F, A] = StateT.liftF(ga)
}
