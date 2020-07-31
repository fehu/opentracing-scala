package com.github.fehu.opentracing.v2.internal

import cats.{ Applicative, CommutativeApplicative, Defer, Monad, MonadError, Parallel, ~> }
import cats.data.{ IndexedStateT, StateT }
import cats.effect.{ CancelToken, ConcurrentEffect, ExitCase, Fiber, IO, Resource, Sync, SyncIO }
import cats.instances.list._
import cats.instances.option._
import cats.syntax.apply._
import cats.syntax.applicative._
import cats.syntax.applicativeError._
import cats.syntax.either._
import cats.syntax.flatMap._
import cats.syntax.foldable._
import cats.syntax.functor._
import cats.syntax.monadError._
import cats.syntax.traverse._
import io.opentracing.propagation.Format
import io.opentracing.{ Span, SpanContext }

import com.github.fehu.opentracing.v2.{ Traced, Traced2 }
import com.github.fehu.opentracing.v2.Traced.ActiveSpan
import com.github.fehu.opentracing.v2.transformer.TracedT

private[opentracing] trait TracedTTracedInstances extends TracedTTracedLowPriorityInstances {
  /**
   *  @note [[Defer]] and [[MonadError]] for [[TracedT]] are provided by
   *        [[IndexedStateT.catsDataDeferForIndexedStateT]] and [[IndexedStateT.catsDataMonadErrorForIndexedStateT]].
   */
  implicit def tracedStateTracedInstance[F[_]: Defer: MonadError[*[_], Throwable]]: Traced2[TracedT, F] = new TracedTTracedInstance

  implicit def tracedTParallelInstance[F[_]](implicit par: Parallel[F]): Parallel.Aux[TracedT[F, *], TracedTParallelInstance.Par[par.F, *]] =
    new TracedTParallelInstance[F, par.F]()(par)

  implicit def tracedTConcurrentEffectInstance[F[_]: ConcurrentEffect](implicit params: Traced.RunParams): ConcurrentEffect[TracedT[F, *]] =
    new TracedTConcurrentEffectInstance[F]

  implicit def tracedTConcurrentEffectInstance2[F[_]: ConcurrentEffect]
                                               (implicit partial: Traced.RunParams.Partial,
                                                         active: ActiveSpan
                                               ): ConcurrentEffect[TracedT[F, *]] =
    new TracedTConcurrentEffectInstance[F]()(ConcurrentEffect[F], partial(active))

}

private[opentracing] trait TracedTTracedLowPriorityInstances {
  implicit def tracedTSyncInstance[F[_]: Sync]: Sync[TracedT[F, *]] = new TracedTSyncInstance[F]
}

private[opentracing] class TracedTTracedInstance[F[_]](implicit D: Defer[F], M: MonadError[F, Throwable])
  extends TracedTTracedInstance.TracedInterface[F] with Traced2[TracedT, F] { self =>

  import TracedTTracedInstance._

  private def state = StateT.get[F, State]
  private def delay = Tools.delay[F]

  def pure[A](a: A): TracedT[F, A] = StateT.pure(a)

  def defer[A](fa: => TracedT[F, A]): TracedT[F, A] = StateT.liftF(delay(fa)).flatMap(locally)

  def lift[A](fa: F[A]): TracedT[F, A] = StateT.liftF(fa)

  def currentSpan: Traced.SpanInterface[TracedT[F, *]] = new CurrentSpan[TracedT[F, *]](state.map(_.currentSpan))

  def forceCurrentSpan(active: ActiveSpan): TracedT[F, Traced.SpanInterface[TracedT[F, *]]] =
    StateT.modify[F, State](_.copy(currentSpan = active.maybe))
          .as(currentSpan)

  def recoverCurrentSpan(active: ActiveSpan): TracedT[F, Traced.SpanInterface[TracedT[F, *]]] =
    StateT.get[F, State].flatMap(
      _.currentSpan
       .map(_ => pure(currentSpan))
       .getOrElse(forceCurrentSpan(active))
    )

  protected def spanParent: TracedT[F, Option[Either[Span, SpanContext]]] = state.map(_.currentSpan.map(Left(_)))

  def injectContext(context: SpanContext): Traced.Interface[TracedT[F, *]] = new InjectInterface(StateT.pure(context))

  def injectContextFrom[C](format: Format[C])(carrier: C): Traced.Interface[TracedT[F, *]] =
    new InjectInterface(
      for {
        s <- state
        c <- StateT liftF delay{ s.tracer.extract(format, carrier) }
      } yield c
    )

  private class InjectInterface(context: TracedT[F, SpanContext]) extends TracedInterface[F] {
    protected def spanParent: TracedT[F, Option[Either[Span, SpanContext]]] = context.map(c => Option(c).map(Right(_)))
  }

  def extractContext[C0 <: C, C](carrier: C0, format: Format[C]): TracedT[F, Option[C0]] =
    for {
      s <- state
      o <- StateT liftF s.currentSpan.traverse(span => delay(s.tracer.inject(span.context(), format, carrier)))
    } yield o.map(_ => carrier)


  def currentRunParams: TracedT[F, Traced.RunParams] =
    state.map(s => Traced.RunParams(s.tracer, s.hooks, ActiveSpan(s.currentSpan)))

  def run[A](traced: TracedT[F, A], params: Traced.RunParams): F[A] =
    traced.run(State(params.tracer, params.hooks, params.activeSpan.maybe)).map(_._2)

  def mapK[G[_]](f: F ~> G): TracedT[F, *] ~> TracedT[G, *] = λ[TracedT[F, *] ~> TracedT[G, *]](_.mapK(f))
}

object TracedTTracedInstance {
  abstract class TracedInterface[F[_]: Defer: MonadError[*[_], Throwable]] extends Traced.Interface[TracedT[F, *]] {
    protected def spanParent: TracedT[F, Option[Either[Span, SpanContext]]]

    private def state = StateT.get[F, State]
    private def setState = StateT.set[F, State] _

    private def delay = Tools.delay[F]

    def apply[A](op: String, tags: Traced.Tag*)(fa: TracedT[F, A]): TracedT[F, A] =
      for {
        s    <- state
        p    <- spanParent
        span <- StateT liftF Tools.newSpan(s.tracer, p, s.hooks.beforeStart, op, tags)
        a    <- execWithSpan(s, span, fa)
      } yield a

    def spanResource(op: String, tags: Traced.Tag*): Resource[TracedT[F, *], ActiveSpan] =
      Resource.makeCase(
        for {
          s    <- state
          p    <- spanParent
          span <- StateT liftF Tools.newSpan(s.tracer, p, s.hooks.beforeStart, op, tags)
          span1 = CurrentSpan(span)
          _    <- StateT liftF s.hooks.justAfterStart(CurrentSpan(span)).traverse_(_(span1))
          _    <- setState(s.copy(currentSpan = Some(span)))
        } yield ActiveSpan(span)
      ) {
        case (span, ExitCase.Completed) => finSpan(span, None)
        case (span, ExitCase.Canceled)  => finSpan(span, Some(new Exception("Canceled")))
        case (span, ExitCase.Error(e))  => finSpan(span, Some(e))
      }

    private def execWithSpan[A](state: State, span: Span, fa: TracedT[F, A]) = {
      val span1 = CurrentSpan(span)
      for {
        _ <- StateT liftF state.hooks.justAfterStart(span1).traverse_(_(span1))
        _ <- setState(state.copy(currentSpan = Some(span)))
        fin = (e: Option[Throwable]) => state.hooks.beforeStop(span1)(e).traverse_(_(span1))
                                             .guarantee0(_ => delay{ span.finish() })
        a <- fa.transformF(_.guarantee0(e => fin(e.left.toOption)))
      } yield a
    }

    private def finSpan(span: ActiveSpan, e: Option[Throwable]): TracedT[F, Unit] =
      for {
        s    <- state
        span1 = CurrentSpan(span.maybe)
        _    <- StateT liftF s.hooks.beforeStop(CurrentSpan(span.maybe))(e).traverse_(_(span1))
                                    .guarantee0(_ => delay{ span.maybe.foreach(_.finish()) })
      } yield ()

    private implicit class GuaranteeOps[A](fa: F[A]) {
      def guarantee0(f: Either[Throwable, A] => F[Unit]): F[A] =
        for {
          ea <- fa.attempt
          _  <- f(ea)
          a  <- ea.pure[F].rethrow
        } yield a
    }
  }
}

private[opentracing] class TracedTSyncInstance[F[_]](implicit sync: Sync[F]) extends Sync[TracedT[F, *]] {
  private lazy val M = IndexedStateT.catsDataMonadErrorForIndexedStateT[F, State, Throwable]

  def suspend[A](thunk: => TracedT[F, A]): TracedT[F, A] = StateT.liftF(sync.delay(thunk)).flatMap(locally)

  def bracketCase[A, B](acquire: TracedT[F, A])
                       (use: A => TracedT[F, B])
                       (release: (A, ExitCase[Throwable]) => TracedT[F, Unit]): TracedT[F, B] =
    for {
      s0      <- StateT.get[F, State]
      (s1, b) <- StateT liftF sync.bracketCase(
                  acquire.run(s0)
                 ){
                   case (s, a) => use(a).run(s)
                 } {
                   case ((s, a), e) => release(a, e).run(s).void
                 }
      _       <- StateT.set[F, State](s1)
    } yield b

  def pure[A](x: A): TracedT[F, A] = StateT.pure(x)

  override def map[A, B](fa: TracedT[F, A])(f: A => B): TracedT[F, B] = fa.map(f)

  def flatMap[A, B](fa: TracedT[F, A])(f: A => TracedT[F, B]): TracedT[F, B] = fa.flatMap(f)

  def tailRecM[A, B](a: A)(f: A => TracedT[F, Either[A, B]]): TracedT[F, B] = M.tailRecM(a)(f)
  def raiseError[A](e: Throwable): TracedT[F, A] = M.raiseError(e)
  def handleErrorWith[A](fa: TracedT[F, A])(f: Throwable => TracedT[F, A]): TracedT[F, A] = M.handleErrorWith(fa)(f)
}

class TracedTConcurrentEffectInstance[F[_]](
  implicit
  ce: ConcurrentEffect[F],
  params: Traced.RunParams
) extends TracedTSyncInstance[F] with ConcurrentEffect[TracedT[F, *]] {

  private def state = StateT.get[F, State]
  private def setState = StateT.set[F, State] _

  private def runP[A](traced: TracedT[F, A]) =
    traced.run(State(params.tracer, params.hooks, params.activeSpan.maybe))
          .map(_._2)

  private def run[A](s: State)(traced: TracedT[F, A]) = traced.run(s).map(_._2)
  private def runVoid[A](s: State)(traced: TracedT[F, A]) = traced.run(s).void

  def async[A](k: (Either[Throwable, A] => Unit) => Unit): TracedT[F, A] = StateT liftF ce.async(k)

  def asyncF[A](k: (Either[Throwable, A] => Unit) => TracedT[F, Unit]): TracedT[F, A] =
    for {
      s0 <- state
      a  <- StateT liftF ce.asyncF[A](k andThen runVoid(s0))
    } yield a

  def runAsync[A](fa: TracedT[F, A])(cb: Either[Throwable, A] => IO[Unit]): SyncIO[Unit] =
    ce.runAsync[A](runP(fa))(cb)

  def runCancelable[A](fa: TracedT[F, A])(cb: Either[Throwable, A] => IO[Unit]): SyncIO[CancelToken[TracedT[F, *]]] =
    ce.runCancelable(runP(fa))(cb).map(StateT.liftF[F, State, Unit])

  def start[A](fa: TracedT[F, A]): TracedT[F, Fiber[TracedT[F, *], A]] =
    for {
      s0 <- state
      f  <- StateT liftF ce.start(run(s0)(fa))
    } yield f.mapK(StateT.liftK[F, State] andThen λ[TracedT[F, *] ~> TracedT[F, *]](_ <* setState(s0)))
  // TODO

  def racePair[A, B](fa: TracedT[F, A], fb: TracedT[F, B]): TracedT[F, Either[(A, Fiber[TracedT[F, *], B]), (Fiber[TracedT[F, *], A], B)]] =
    for {
      s0 <- state
      ef <- StateT liftF ce.racePair(run(s0)(fa), run(s0)(fb))
    } yield ef.leftMap{ case (a, f) => a -> f.mapK(StateT.liftK[F, State]) }
                 .map { case (f, b) => f.mapK(StateT.liftK[F, State]) -> b }

}

class TracedTParallelInstance[G[_], ParF[_]](implicit val par0: Parallel.Aux[G, ParF]) extends Parallel[TracedT[G, *]] {
  import TracedTParallelInstance.Par

  type F[A] = Par[ParF, A]

  def applicative: Applicative[Par[ParF, *]] = Par.parCommutativeApplicative0(par0.applicative)
  def monad: Monad[TracedT[G, *]] = IndexedStateT.catsDataMonadForIndexedStateT[G, State](par0.monad)

  def sequential: Par[ParF, *] ~> TracedT[G, *] =
    λ[Par[ParF, *] ~> TracedT[G, *]](_.traced.mapK(par0.sequential)(par0.applicative))
  def parallel: TracedT[G, *] ~> Par[ParF, *] =
    λ[TracedT[G, *] ~> Par[ParF, *]](t => new Par(t.mapK(par0.parallel)(par0.monad)))
}

object TracedTParallelInstance {
  class Par[F[_], A](val traced: TracedT[F, A]) extends AnyVal

  object Par {
    implicit def parCommutativeApplicative[F[_]: CommutativeApplicative]: CommutativeApplicative[Par[F, *]] =
      parCommutativeApplicative0[F]

    protected[TracedTParallelInstance] def parCommutativeApplicative0[F[_]](implicit A: Applicative[F]): CommutativeApplicative[Par[F, *]] =
      new CommutativeApplicative[Par[F, *]] {
        def pure[A](x: A): Par[F, A] = new Par(StateT.pure(x))

        def ap[A, B](ff: Par[F, A => B])(fa: Par[F, A]): Par[F, B] =
          new Par(StateT.applyF {
            A.product(ff.traced.runF, fa.traced.runF)
             .map { case (rff, rfa) =>
              (s: State) =>
                A.ap(rff(s).map(_._2))(rfa(s).map(_._2))
                 .map((s, _))
            }
          })
      }
  }
}
