package com.github.fehu.opentracing.internal

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.FiniteDuration

import cats.{ Applicative, CommutativeApplicative, Monad, MonadError, Parallel, ~> }
import cats.data.{ IndexedStateT, StateT }
import cats.effect._
import cats.effect.kernel.CancelScope
import cats.effect.kernel.Resource.ExitCase
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

import com.github.fehu.opentracing.{ Traced, Traced2 }
import com.github.fehu.opentracing.Traced.ActiveSpan
import com.github.fehu.opentracing.transformer.TracedT
import com.github.fehu.opentracing.transformer.TracedT.AutoConvert._

private[opentracing] trait TracedTInstances extends TracedTLowPriorityInstances1 {

  implicit def tracedTTracedInstance[F[_]: Sync]: Traced2[TracedT, F] =
    new TracedTTracedInstance

  implicit def tracedTParallelInstance[F[_]](implicit par: Parallel[F]): Parallel.Aux[TracedT[F, *], TracedTParallelInstance.Par[par.F, *]] =
    new TracedTParallelInstance[F, par.F]()(par)

  implicit def tracedTAsyncInstance[F[_]: Async]: Async[TracedT[F, *]] = new TracedTAsyncInstance[F] { protected val F: Async[F] = Async[F] }
}


private[opentracing] trait TracedTLowPriorityInstances1 extends TracedTLowPriorityInstances2 {
  implicit def tracedTSyncInstance[F[_]: Sync]: Sync[TracedT[F, *]] = new TracedTSyncProxy[F] {
    protected val F: Sync[F] = Sync[F]
    protected val C: Clock[F] = F
    def rootCancelScope: CancelScope = F.rootCancelScope
  }

  implicit def tracedTGenTemporalInstance[F[_], E: GenTemporal[F, *]]: GenTemporal[TracedT[F, *], E] =
    new TracedTGenTemporalInstance[F, E] {
      protected val F: GenTemporal[F, E] = GenTemporal[F, E]
    }

  implicit def tracedTLiftIoInstance[F[_]: Applicative: LiftIO]: LiftIO[TracedT[F, *]] = new TracedTLiftIoInstance
}

private[opentracing] trait TracedTLowPriorityInstances2 extends TracedTLowPriorityInstances3 {
  implicit def tracedTGenConcurrentInstance[F[_], E: GenConcurrent[F, *]]: GenConcurrent[TracedT[F, *], E] =
    new TracedTGenConcurrentInstance[F, E] { protected val F: GenConcurrent[F, E] = GenConcurrent[F, E] }
}

private[opentracing] trait TracedTLowPriorityInstances3 extends TracedTLowPriorityInstances4 {
  implicit def tracedTGenSpawnInstance[F[_], E: GenSpawn[F, *]]: GenSpawn[TracedT[F, *], E] =
    new TracedTGenSpawnInstance[F, E] { protected val F: GenSpawn[F, E] = GenSpawn[F, E] }
}

private[opentracing] trait TracedTLowPriorityInstances4 extends TracedTLowPriorityInstances5 {
  implicit def tracedTMonadCancelInstance[F[_], E](implicit M: MonadCancel[F, E]): MonadCancel[TracedT[F, *], E] =
    new TracedTMonadCancelProxy[F, E] {
      protected val F: MonadCancel[F, E] = M
      def rootCancelScope: CancelScope = M.rootCancelScope
    }

  implicit def tracedTClockInstance[F[_]: Monad: Clock]: Clock[TracedT[F, *]] = new TracedTClockProxy[F] {
    protected val F: Monad[F] = Monad[F]
    protected val C: Clock[F] = Clock[F]
  }
}

private[opentracing] trait TracedTLowPriorityInstances5 extends TracedTLowPriorityInstances6 {
  implicit def tracedTMonadErrorInstance[F[_], E](implicit M: MonadError[F, E]): MonadError[TracedT[F, *], E] =
    new TracedTMonadErrorProxy[F, E] { protected val F: MonadError[F, E] = M }
}

private[opentracing] trait TracedTLowPriorityInstances6 {
  implicit def tracedTMonadInstance[F[_]](implicit M: Monad[F]): Monad[TracedT[F, *]] =
    new TracedTMonadProxy[F] { protected val F: Monad[F] = M }
}

// // //

private[opentracing] class TracedTTracedInstance[F[_]](implicit sync: Sync[F])
  extends TracedTTracedInstance.TracedInterface[F] with Traced2[TracedT, F] { self =>

  import sync.delay
  import TracedTTracedInstance._

  private def state = StateT.get[F, State]

  def pure[A](a: A): TracedT[F, A] = TracedT(StateT.pure(a))

  def defer[A](fa: => TracedT[F, A]): TracedT[F, A] = StateT.liftF(delay(fa.stateT)).flatMap(locally)

  def lift[A](fa: F[A]): TracedT[F, A] = TracedT(StateT.liftF(fa))

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

  protected def spanParent: TracedT[F, Option[Either[Span, SpanContext]]] = TracedT(state.map(_.currentSpan.map(Left(_))))

  def injectContext(context: SpanContext): Traced.Interface[TracedT[F, *]] = InterfaceProxy.pure(Some(Right(context)))

  def injectContextFrom[C](format: Format[C])(carrier: C): Traced.Interface[TracedT[F, *]] =
    new InterfaceProxy(
      TracedT(
        for {
          s  <- state
          ce <- StateT liftF delay{ s.tracer.extract(format, carrier) }.attempt
          _  <- StateT.liftF(ce.swap.traverse_(s.logError[F]("Failed to extract span context from carrier", _)))
        } yield ce.toOption.map(_.asRight)
      )
    )

  private class InterfaceProxy(parent: TracedT[F, Option[Either[Span, SpanContext]]]) extends TracedInterface[F] {
    protected def spanParent: TracedT[F, Option[Either[Span, SpanContext]]] = parent

    def withParent(span: ActiveSpan): Traced.Interface[TracedT[F, *]] =
      InterfaceProxy.pure(Option(span).flatMap(_.maybe).map(_.asLeft))

    def withParent(span: SpanContext): Traced.Interface[TracedT[F, *]] =
      InterfaceProxy.pure(Option(span).map(_.asRight))

    def withoutParent: Traced.Interface[TracedT[F, *]] =
      InterfaceProxy.pure(None)
  }
  private object InterfaceProxy {
    def pure(opt: Option[Either[Span, SpanContext]]): InterfaceProxy = new InterfaceProxy(self.pure(opt))
  }

  def extractContext[C0 <: C, C](carrier: C0, format: Format[C]): TracedT[F, Option[C0]] =
    for {
      s <- state
      o <- StateT liftF s.currentSpan.traverse(span => delay(s.tracer.inject(span.context(), format, carrier)))
    } yield o.map(_ => carrier)


  def currentRunParams: TracedT[F, Traced.RunParams] = state.map(_.toRunParams)

  def run[A](traced: TracedT[F, A], params: Traced.RunParams): F[A] = traced.runA(State.fromRunParams(params))

  def mapK[G[_]](f: F ~> G): TracedT[F, *] ~> TracedT[G, *] = λ[TracedT[F, *] ~> TracedT[G, *]](_.mapK(f))

  def withParent(span: ActiveSpan): Traced.Interface[TracedT[F, *]] = withParent0(Option(span).flatMap(_.maybe).map(_.asLeft))
  def withParent(span: SpanContext): Traced.Interface[TracedT[F, *]] = withParent0(Option(span).map(_.asRight))
  def withoutParent: Traced.Interface[TracedT[F, *]] = withParent0(None)

  private def withParent0(span: Option[Either[Span, SpanContext]]): Traced.Interface[TracedT[F, *]] =
    new InterfaceProxy(pure(span))
}

object TracedTTracedInstance {
  abstract class TracedInterface[F[_]](implicit sync: Sync[F]) extends Traced.Interface[TracedT[F, *]] {
    protected def spanParent: TracedT[F, Option[Either[Span, SpanContext]]]

    import sync.delay

    private def state = StateT.get[F, State]
    private def setState = StateT.set[F, State] _

    def apply[A](op: String, tags: Traced.Tag*)(fa: TracedT[F, A]): TracedT[F, A] =
      spanResource(op, tags: _*).use { activeSpan =>
        for {
          s <- state
          _ <- setState(s.copy(currentSpan = activeSpan.maybe))
          a <- fa
        } yield a
      }

    def spanResource(op: String, tags: Traced.Tag*): Resource[TracedT[F, *], ActiveSpan] =
      Resource.makeCase[TracedT[F, *], ActiveSpan](
        for {
          s    <- state
          p    <- spanParent
          span <- StateT liftF Tools.newSpan(s.tracer, p, s.hooks.beforeStart, op, tags)
          span1 = CurrentSpan(span)
          _    <- StateT liftF s.hooks.justAfterStart(CurrentSpan(span)).traverse_(_(span1))
          _    <- setState(s.copy(currentSpan = Some(span)))
        } yield ActiveSpan(span)
      ) {
        case (span, ExitCase.Succeeded)  => finSpan(span, None)
        case (span, ExitCase.Canceled)   => finSpan(span, Some(new Exception("Canceled")))
        case (span, ExitCase.Errored(e)) => finSpan(span, Some(e))
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

private[opentracing] trait TracedTMonadProxy[F[_]] extends Monad[TracedT[F, *]] {
  protected val F: Monad[F]
  private lazy val M0 = IndexedStateT.catsDataMonadForIndexedStateT[F, State](F)

  override def map[A, B](fa: TracedT[F, A])(f: A => B): TracedT[F, B] = M0.map(fa)(f)
  def pure[A](x: A): TracedT[F, A] = M0.pure(x)
  def flatMap[A, B](fa: TracedT[F, A])(f: A => TracedT[F, B]): TracedT[F, B] = M0.flatMap(fa)(f.andThen(_.stateT))
  def tailRecM[A, B](a: A)(f: A => TracedT[F, Either[A, B]]): TracedT[F, B] = M0.tailRecM(a)(f.andThen(_.stateT))
}

private[opentracing] trait TracedTMonadErrorProxy[F[_], E] extends MonadError[TracedT[F, *], E] with TracedTMonadProxy[F] {
  protected val F: MonadError[F, E]
  private lazy val M0 = IndexedStateT.catsDataMonadErrorForIndexedStateT[F, State, E](F)

  def raiseError[A](e: E): TracedT[F, A] = TracedT(M0.raiseError(e))
  def handleErrorWith[A](fa: TracedT[F, A])(f: E => TracedT[F, A]): TracedT[F, A] = M0.handleErrorWith(fa)(f.andThen(_.stateT))
}

private[opentracing] trait TracedTMonadCancelProxy[F[_], E] extends MonadCancel[TracedT[F, *], E] with TracedTMonadErrorProxy[F, E] {
  protected val F: MonadCancel[F, E]
  private lazy val M0 = MonadCancel.monadCancelForStateT[F, State, E](F)

  def forceR[A, B](fa: TracedT[F, A])(fb: TracedT[F, B]): TracedT[F, B] = M0.forceR(fa)(fb)
  def uncancelable[A](body: Poll[TracedT[F, *]] => TracedT[F, A]): TracedT[F, A] =
    M0.uncancelable(poll => body(pollK(TracedT.toK, TracedT.fromK, poll)))
  def canceled: TracedT[F, Unit] = M0.canceled
  def onCancel[A](fa: TracedT[F, A], fin: TracedT[F, Unit]): TracedT[F, A] = M0.onCancel(fa, fin)

  private def pollK[X[_], Y[_]](fxy: X ~> Y, fyx: Y ~> X, px: Poll[X]): Poll[Y] =
    new Poll[Y] {
      def apply[A](fa: Y[A]): Y[A] = fxy(px.apply(fyx(fa)))
    }
}

private[opentracing] trait TracedTClockProxy[F[_]] extends Clock[TracedT[F, *]] {
  protected val F: Monad[F]
  protected val C: Clock[F]
  private lazy val M0 = Clock.clockForStateT[F, State](F, C)

  def applicative: Applicative[TracedT[F, *]] = TracedT.tracedTMonadInstance(F)
  def monotonic: TracedT[F, FiniteDuration] = M0.monotonic
  def realTime: TracedT[F, FiniteDuration] = M0.realTime
}

private[opentracing] trait TracedTSyncProxy[F[_]] extends Sync[TracedT[F, *]]
  with TracedTMonadCancelProxy[F, Throwable]
  with TracedTClockProxy[F]
{
  protected val F: Sync[F]
  private lazy val M0 = Sync.syncForStateT[F, State](F)

  override def applicative: Applicative[TracedT[F, *]] = super[Sync].applicative

  def suspend[A](hint: Sync.Type)(thunk: => A): TracedT[F, A] = M0.suspend(hint)(thunk)
}

private[opentracing] class TracedTFiber[F[_]: Monad, E, A](f: Fiber[F, E, A], s0: State) extends Fiber[TracedT[F, *], E, A] {
  def cancel: TracedT[F, Unit] = TracedT.liftF(f.cancel)
  def join: TracedT[F, Outcome[TracedT[F, *], E, A]] =
    TracedT.liftF(f.join.map(_.mapK(TracedT.liftK))) <*
      TracedT(StateT.set[F, State](s0))
}

private[opentracing] trait TracedTGenSpawnInstance[F[_], E] extends GenSpawn[TracedT[F, *], E] with TracedTMonadCancelProxy[F, E] {
  protected val F: GenSpawn[F, E]

  private[this] implicit def F0: GenSpawn[F, E] = F
  protected[this] def run[A](s: State)(traced: TracedT[F, A]) = traced.run(s).map(_._2)

  def start[A](fa: TracedT[F, A]): TracedT[F, Fiber[TracedT[F, *], E, A]] = TracedT(
    for {
      s0 <- StateT.get[F, State]
      f  <- StateT liftF F.start(run(s0)(fa))
    } yield new TracedTFiber(f, s0)
  )

  def racePair[A, B](fa: TracedT[F, A], fb: TracedT[F, B]): TracedT[F, Either[(Outcome[TracedT[F, *], E, A], Fiber[TracedT[F, *], E, B]), (Fiber[TracedT[F, *], E, A], Outcome[TracedT[F, *], E, B])]] =
    TracedT(
      for {
        s0 <- StateT.get[F, State]
        ef <- StateT liftF F.racePair(run(s0)(fa), run(s0)(fb))
      } yield ef.bimap(
        { case (out, fib) => out.mapK(TracedT.liftK) -> new TracedTFiber(fib, s0) },
        { case (fib, out) => new TracedTFiber(fib, s0) -> out.mapK(TracedT.liftK) },
      )
    )

  def never[A]: TracedT[F, A] = TracedT.liftF(F.never)
  def cede: TracedT[F, Unit] = TracedT.liftF(F.cede)
  def unique: TracedT[F, Unique.Token] = TracedT.liftF(F.unique)
}

private[opentracing] trait TracedTGenConcurrentInstance[F[_], E] extends GenConcurrent[TracedT[F, *], E] with TracedTGenSpawnInstance[F, E] {
  protected val F: GenConcurrent[F, E]
  private[this] implicit def F0: GenConcurrent[F, E] = F

  def ref[A](a: A): TracedT[F, Ref[TracedT[F, *], A]] = TracedT.liftF(F.ref(a).map(_.mapK(TracedT.liftK)))
  def deferred[A]: TracedT[F, Deferred[TracedT[F, *], A]] = TracedT.liftF(F.deferred[A].map(_.mapK(TracedT.liftK)))

  override def racePair[A, B](fa: TracedT[F, A], fb: TracedT[F, B]) = super[GenConcurrent].racePair(fa, fb)
}

private[opentracing] trait TracedTGenTemporalInstance[F[_], E] extends GenTemporal[TracedT[F, *], E]
  with TracedTGenConcurrentInstance[F, E]
  with TracedTClockProxy[F]
{
  protected val F: GenTemporal[F, E]
  protected val C: Clock[F] = F

  def sleep(time: FiniteDuration): TracedT[F, Unit] = TracedT.liftF(F.sleep(time))(F)

  override def applicative: Applicative[TracedT[F, *]] = super[GenTemporal].applicative
}

private[opentracing] class TracedTCont[F[_]: Sync, E, A](c: Cont[TracedT[F, *], E, A], s0: State) extends Cont[F, E, A] {
  def apply[G[_]](implicit G: MonadCancel[G, Throwable]): (Either[Throwable, E] => Unit, G[E], F ~> G) => G[A] =
    (ef, g, tf) => c(G)(ef, g, tf.compose(TracedT.runK(s0.toRunParams)))
}

private[opentracing] trait TracedTAsyncInstance[F[_]] extends Async[TracedT[F, *]]
  with TracedTGenTemporalInstance[F, Throwable]
  with TracedTSyncProxy[F]
{
  protected val F: Async[F]
  private[this] implicit def F0: Async[F] = F

  def evalOn[A](fa: TracedT[F, A], ec: ExecutionContext): TracedT[F, A] =
    for {
      s0 <- StateT.get[F, State]
      (_, a) <- StateT.liftF(F.evalOn(fa.run(s0), ec))
      // TODO
      // _ <- StateT.set[F, State](s1)
    } yield a

  def executionContext: TracedT[F, ExecutionContext] = TracedT.liftF(F.executionContext)

  def cont[K, R](body: Cont[TracedT[F, *], K, R]): TracedT[F, R] =
    for {
      s0 <- StateT.get[F, State]
      r <- StateT.liftF(F.cont(new TracedTCont(body, s0)))
    } yield r

  override def never[A]: TracedT[F, A] = super[Async].never
  override def unique: TracedT[F, Unique.Token] = super[TracedTSyncProxy].unique
}

private[opentracing] trait TracedTLiftIO[F[_]] extends LiftIO[TracedT[F, *]] {
  protected val AF: Applicative[F]
  protected val LIOF: LiftIO[F]

  def liftIO[A](ioa: IO[A]): TracedT[F, A] = StateT.liftF[F, State, A](LIOF.liftIO(ioa))(AF)
}

private[opentracing] class TracedTLiftIoInstance[F[_]](implicit protected val AF: Applicative[F],
                                                                protected val LIOF: LiftIO[F])
  extends TracedTLiftIO[F]

class TracedTParallelInstance[G[_], ParF[_]](implicit val par0: Parallel.Aux[G, ParF]) extends Parallel[TracedT[G, *]] {
  import TracedTParallelInstance.Par

  type F[A] = Par[ParF, A]

  def applicative: Applicative[Par[ParF, *]] = Par.parCommutativeApplicative0(par0.applicative)
  def monad: Monad[TracedT[G, *]] = new TracedTMonadProxy[G] { protected val F: Monad[G] = par0.monad }

  def sequential: Par[ParF, *] ~> TracedT[G, *] =
    λ[Par[ParF, *] ~> TracedT[G, *]](_.traced.mapK(par0.sequential)(par0.applicative))
  def parallel: TracedT[G, *] ~> Par[ParF, *] =
    λ[TracedT[G, *] ~> Par[ParF, *]](t => new Par(t.mapK(par0.parallel)(par0.monad)))
}

object TracedTParallelInstance {
  class Par[F[_], A](val traced: TracedT.Underlying[F, A]) extends AnyVal

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
