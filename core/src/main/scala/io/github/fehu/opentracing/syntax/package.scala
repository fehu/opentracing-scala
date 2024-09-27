package io.github.fehu.opentracing

import scala.language.existentials

import cats.effect.{ Resource, Sync }
import cats.syntax.flatMap.*
import cats.syntax.functor.*
import cats.{ Applicative, Defer, FlatMap, Functor, Monad, ~> }
import io.opentracing.{ SpanContext, Tracer }

import io.github.fehu.opentracing.internal.compat.FK
import io.github.fehu.opentracing.internal.syntax.LowPrioritySyntax
import io.github.fehu.opentracing.propagation.Propagation
import io.github.fehu.opentracing.util.ErrorLogger

package object syntax extends LowPrioritySyntax {

  final implicit class TracedOps[F[_], A](fa: F[A])(implicit traced: Traced[F]) {
    def trace(operation: String, tags: Traced.Tag*): F[A] = traced(operation, tags*)(fa)

    def inject(context: SpanContext)(operation: String, tags: Traced.Tag*): F[A] =
      traced.injectContext(context)(operation, tags*)(fa)

    def inject(context: Option[SpanContext])(operation: String, tags: Traced.Tag*): F[A] =
      context.map(inject(_)(operation, tags*)).getOrElse(fa)

    def injectFrom(carrier: Propagation#Carrier)(operation: String, tags: Traced.Tag*): F[A] =
      traced.injectContextFrom(carrier)(operation, tags*)(fa)

    def injectFromOpt(carrier: Option[Propagation#Carrier])(operation: String, tags: Traced.Tag*): F[A] =
      carrier.map(injectFrom(_)(operation, tags*)).getOrElse(fa)

    def injectPropagated(carrier: Propagation#Carrier)(operation: String, tags: Traced.Tag*): F[A] =
      traced.injectContextFrom(carrier)(operation, tags*)(fa)

    def injectPropagatedOpt(carrier: Option[Propagation#Carrier])(operation: String, tags: Traced.Tag*): F[A] =
      carrier.map(injectPropagated(_)(operation, tags*)).getOrElse(fa)
  }

  sealed trait TracedFunctions {
    def currentSpan[F[_]](implicit traced: Traced[F]): Traced.SpanInterface[F] = traced.currentSpan

    def extractContext[F[_]]: TracedFunctions.Extract[F] =
      TracedFunctions.extractInstance.asInstanceOf[TracedFunctions.Extract[F]]

    def mapK[T[_[_], _], F[_], G[_]: Functor](f: F ~> G)(implicit traced: Traced2[T, F]): T[F, _] ~> T[G, _] =
      traced.mapK(f)

    def trace[F[_]](operation: String, tags: Traced.Tag*): TracedFunctions.Trace[F] =
      new TracedFunctions.Trace(operation, tags)

    def traceK[F[_]](operation: String, tags: Traced.Tag*)(implicit traced: Traced[F]): F ~> F =
      FK.lift[F, F](f => traced(operation, tags*)(f))

    def pure[T[_[_], _], F[_]]: TracedFunctions.Pure[F] =
      TracedFunctions.pureInstance.asInstanceOf[TracedFunctions.Pure[F]]

    def defer[F[_]]: TracedFunctions.Defer[F] = TracedFunctions.deferInstance.asInstanceOf[TracedFunctions.Defer[F]]

    def delay[F[_]]: TracedFunctions.Delay[F] = TracedFunctions.delayInstance.asInstanceOf[TracedFunctions.Delay[F]]

    def liftK[T[_[_], _], F[_]: Applicative](implicit traced: Traced2[T, F]): F ~> T[F, _] =
      FK.lift[F, T[F, _]](f => traced.lift(f))

    def runK[T[_[_], _], F[_]: FlatMap](params: Traced.RunParams)(implicit traced: Traced2[T, F]): T[F, _] ~> F =
      FK.lift[T[F, _], F](t => traced.run(t, params))

  }

  object TracedFunctions extends TracedFunctions {
    final class Extract[F[_]] private[syntax] () {
      def apply[C <: Propagation#Carrier](carrier: C)(implicit traced: Traced[F]): F[Option[C]] =
        traced.extractContext(carrier)

      def to[P <: Propagation](
          propagation: P
      )(implicit traced: Traced[F], sync: Sync[F]): F[Option[propagation.Carrier]] =
        for {
          carrier <- sync.delay(propagation())
          uOpt    <- apply(carrier)
        } yield uOpt.as(carrier)
    }
    final class Trace[F[_]] private[syntax] (operation: String, tags: Seq[Traced.Tag]) {
      def apply[A](a: => A)(implicit traced: Traced[F]): F[A] = traced(operation, tags*)(traced.defer(traced.pure(a)))
    }
    final class Pure[F[_]] private[syntax] () {
      def apply[A](a: A)(implicit traced: Traced[F]): F[A] = traced.pure(a)
    }
    final class Defer[F[_]] private[syntax] () {
      def apply[A](fa: => F[A])(implicit traced: Traced[F]): F[A] = traced.defer(fa)
    }
    final class Delay[F[_]] private[syntax] () {
      def apply[A](a: => A)(implicit traced: Traced[F]): F[A] = traced.defer(traced.pure(a))
    }
    protected lazy val extractInstance = new TracedFunctions.Extract[cats.Id]
    protected lazy val pureInstance    = new TracedFunctions.Pure[cats.Id]
    protected lazy val deferInstance   = new TracedFunctions.Defer[cats.Id]
    protected lazy val delayInstance   = new TracedFunctions.Delay[cats.Id]
  }

  final implicit class TracedObjOps(obj: Traced.type) extends TracedFunctions

  final implicit class Traced2Ops[F[_[_], _], G[_], A](fa: F[G, A])(implicit traced: Traced2[F, G]) {
    def runTracedP(params: Traced.RunParams): G[A] = traced.run(fa, params)

    def runTraced(implicit active: Traced.ActiveSpan, setup: Traced.Setup): G[A] =
      runTracedP(Traced.RunParams.fromScope)

    def runTraced0(
        tracer: Tracer,
        hooks: Traced.Hooks = Traced.Hooks(),
        parent: Traced.ActiveSpan = Traced.ActiveSpan.empty,
        logError: ErrorLogger = ErrorLogger.stdout
    ): G[A] =
      runTracedP(Traced.RunParams(tracer, hooks, logError, parent))
  }

  final implicit class TracedResourceOps[F[_]: Monad: Defer, A](resource: Resource[F, A])(implicit t: Traced[F]) {
    def traceLifetime(operation: String, tags: Traced.Tag*): Resource[F, A] =
      t.spanResource(operation, tags*).flatMap(_ => resource)

    def traceUsage(operation: String, tags: Traced.Tag*): Resource[F, A] =
      resource.flatTap(_ => t.spanResource(operation, tags*))

    def traceUsage(trace: A => Traced.Operation.Builder): Resource[F, A] =
      resource.flatTap(a => t.spanResource(trace(a)))
  }

}
