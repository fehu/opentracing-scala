package com.github.fehu.opentracing.v2.internal

import cats.{ Applicative, CommutativeApplicative, Defer, Monad, MonadError, Parallel, ~> }
import cats.data.{ IndexedStateT, StateT }
import cats.effect.{ Async, CancelToken, Concurrent, ConcurrentEffect, Effect, ExitCase, Fiber, IO, LiftIO, Resource, Sync, SyncIO }
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

/**
 * {{{
 *  ===============================================
 *                                    = Instances =
 *   +---------+   +----------+       =============
 *   | Traced2 |   | Parallel |
 *   +---------+   +----------+
 *
 *      +------------------+
 *      | ConcurrentEffect |
 *      +------------------+
 *               ▲
 *  ============ | ================================
 *       +-------+-------+              = Lower 1 =
 *       |               |              ===========
 *  +--------+     +------------+
 *  | Effect |     | Concurrent |
 *  +--------+     +------------+
 *       ▲               ▲
 *       |               |
 *       +-------+-------+
 *  ============ | ================================
 *               |                      = Lower 2 =
 *           +-------+                  ===========
 *           | Async |
 *           +-------+
 *               ▲
 *  ============ | ================================
 *       +-------+-------+              = Lower 3 =
 *       |               |              ===========
 *  +--------+       +--------+
 *  |  Sync  |       | LiftIO |
 *  +--------+       +--------+
 *  }}}
 */
private[opentracing] trait TracedTTracedInstances
  extends TracedTTracedLowPriorityInstances1
     with TracedTTracedLowPriorityInstances2
     with TracedTTracedLowPriorityInstances3
{
  implicit def tracedTTracedInstance[F[_]: Defer: MonadError[*[_], Throwable]]: Traced2[TracedT, F] =
    new TracedTTracedInstance

  implicit def tracedTParallelInstance[F[_]](implicit par: Parallel[F]): Parallel.Aux[TracedT[F, *], TracedTParallelInstance.Par[par.F, *]] =
    new TracedTParallelInstance()(par)

  /** Requires implicit [[Traced.RunParams]] in scope.  */
  implicit def tracedTConcurrentEffectInstance[F[_]: ConcurrentEffect](implicit params: Traced.RunParams): ConcurrentEffect[TracedT[F, *]] =
    new TracedTConcurrentEffectInstance
}

private[opentracing] trait TracedTTracedLowPriorityInstances1 {
  implicit def tracedTConcurrentInstance[F[_]: Concurrent]: Concurrent[TracedT[F, *]] = new TracedTConcurrentInstance

  /** Requires implicit [[Traced.RunParams]] in scope.  */
  implicit def tracedTEffectInstance[F[_]: Effect](implicit params: Traced.RunParams): Effect[TracedT[F, *]] = new TracedTEffectInstance
}

private[opentracing] trait TracedTTracedLowPriorityInstances2 {
  implicit def tracedTAsyncInstance[F[_]: Async]: Async[TracedT[F, *]] = new TracedTAsyncInstance
}

private[opentracing] trait TracedTTracedLowPriorityInstances3 {
  implicit def tracedTSyncInstance[F[_]: Sync]: Sync[TracedT[F, *]] = new TracedTSyncInstance
  implicit def tracedTLiftIoInstance[F[_]: Applicative: LiftIO]: LiftIO[TracedT[F, *]] = new TracedTLiftIoInstance
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

private[opentracing] trait TracedTMonadErrorProxy[F[_], E] extends MonadError[TracedT[F, *], E] {
  protected val MF: MonadError[F, E]
  private val M0 = IndexedStateT.catsDataMonadErrorForIndexedStateT[F, State, E](MF)

  override def map[A, B](fa: TracedT[F, A])(f: A => B): TracedT[F, B] = M0.map(fa)(f)
  def pure[A](x: A): TracedT[F, A] = M0.pure(x)
  def flatMap[A, B](fa: TracedT[F, A])(f: A => TracedT[F, B]): TracedT[F, B] = M0.flatMap(fa)(f)
  def tailRecM[A, B](a: A)(f: A => TracedT[F, Either[A, B]]): TracedT[F, B] = M0.tailRecM(a)(f)
  def raiseError[A](e: E): TracedT[F, A] = M0.raiseError(e)
  def handleErrorWith[A](fa: TracedT[F, A])(f: E => TracedT[F, A]): TracedT[F, A] = M0.handleErrorWith(fa)(f)
}

private[opentracing] class TracedTSyncInstance[F[_]](implicit sync: Sync[F])
  extends Sync[TracedT[F, *]] with TracedTMonadErrorProxy[F, Throwable]
{
  protected val MF: MonadError[F, Throwable] = sync
  protected val DF: Defer[F] = sync

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
}

private[opentracing] trait TracedTLiftIO[F[_]] extends LiftIO[TracedT[F, *]] {
  protected val AF: Applicative[F]
  protected val LIOF: LiftIO[F]

  def liftIO[A](ioa: IO[A]): TracedT[F, A] = StateT.liftF[F, State, A](LIOF.liftIO(ioa))(AF)
}

private[opentracing] class TracedTLiftIoInstance[F[_]](implicit protected val AF: Applicative[F],
                                                                protected val LIOF: LiftIO[F])
  extends TracedTLiftIO[F]

private[opentracing] class TracedTAsyncInstance[F[_]](implicit A: Async[F])
  extends TracedTSyncInstance[F]
     with Async[TracedT[F, *]]
     with TracedTLiftIO[F] {
  protected val AF: Applicative[F] = A
  protected val LIOF: LiftIO[F] = A

  protected[this] def state = StateT.get[F, State]
  protected[this] def setState = StateT.set[F, State] _

  override def liftIO[A](ioa: IO[A]): TracedT[F, A] = super[TracedTLiftIO].liftIO(ioa)

  def async[A](k: (Either[Throwable, A] => Unit) => Unit): TracedT[F, A] = StateT.liftF[F, State, A](A.async(k))

  def asyncF[A](k: (Either[Throwable, A] => Unit) => TracedT[F, Unit]): TracedT[F, A] =
    for {
      s0 <- state
      a  <- StateT liftF A.asyncF[A](k andThen runVoid(s0))
    } yield a

  private def runVoid[A](s: State)(traced: TracedT[F, A]) = traced.run(s).void
}

private[opentracing] trait TracedTEffect[F[_]] extends Effect[TracedT[F, *]] {
  protected val EF: Effect[F]

  implicit private val EF0: Effect[F] = EF

  protected val params: Traced.RunParams

  protected[this] def runP[A](traced: TracedT[F, A]) =
    traced.run(State(params.tracer, params.hooks, params.activeSpan.maybe)).map(_._2)

  def runAsync[A](fa: TracedT[F, A])(cb: Either[Throwable, A] => IO[Unit]): SyncIO[Unit] =
    EF.runAsync[A](runP(fa))(cb)


}

private[opentracing] class TracedTEffectInstance[F[_]](implicit protected val EF: Effect[F],
                                                                protected val params: Traced.RunParams)
  extends TracedTAsyncInstance[F] with TracedTEffect[F]

private[opentracing] class TracedTConcurrentInstance[F[_]](implicit C: Concurrent[F])
  extends TracedTAsyncInstance[F]
     with Concurrent[TracedT[F, *]] {

  protected[this] def run[A](s: State)(traced: TracedT[F, A]) = traced.run(s).map(_._2)

  def start[A](fa: TracedT[F, A]): TracedT[F, Fiber[TracedT[F, *], A]] =
    for {
      s0 <- state
      f  <- StateT liftF C.start(run(s0)(fa))
    } yield f.mapK(StateT.liftK[F, State] andThen λ[TracedT[F, *] ~> TracedT[F, *]](_ <* setState(s0)))
  // TODO

  def racePair[A, B](fa: TracedT[F, A], fb: TracedT[F, B]): TracedT[F, Either[(A, Fiber[TracedT[F, *], B]), (Fiber[TracedT[F, *], A], B)]] =
    for {
      s0 <- state
      ef <- StateT liftF C.racePair(run(s0)(fa), run(s0)(fb))
    } yield ef.leftMap{ case (a, f) => a -> f.mapK(StateT.liftK[F, State]) }
                 .map { case (f, b) => f.mapK(StateT.liftK[F, State]) -> b }
}

class TracedTConcurrentEffectInstance[F[_]](
  implicit
  ce: ConcurrentEffect[F],
  protected val params: Traced.RunParams
) extends TracedTConcurrentInstance[F]
     with ConcurrentEffect[TracedT[F, *]]
     with TracedTEffect[F] {
  protected val EF: Effect[F] = ce

  def runCancelable[A](fa: TracedT[F, A])(cb: Either[Throwable, A] => IO[Unit]): SyncIO[CancelToken[TracedT[F, *]]] =
    ce.runCancelable(runP(fa))(cb).map(StateT.liftF[F, State, Unit])
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
