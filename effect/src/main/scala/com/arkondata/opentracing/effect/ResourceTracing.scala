package com.arkondata.opentracing.effect

import cats.{ Applicative, Defer, ~> }
import cats.arrow.FunctionK
import cats.effect.{ ExitCase, Resource, Sync }
import cats.effect.syntax.bracket._
import cats.instances.option._
import cats.syntax.apply._
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.syntax.traverse._
import com.arkondata.opentracing.Tracing.TracingSetup
import com.arkondata.opentracing.{ Tracing, util }
import io.opentracing.{ Scope, Span, Tracer }

object ResourceTracing {
  def lifetimeTracing[F[_]: Sync](implicit t: Tracer, setup: TracingSetup): Tracing[Resource[F, *], Resource[F, *]] =
    new ResourceTracing[F] {
      protected def combine[A](traceR: Resource[F, _], r: Resource[F, A]): Resource[F, A] = traceR *> r
    }

  def usageTracing[F[_]: Sync](implicit t: Tracer, setup: TracingSetup): Tracing[Resource[F, *], Resource[F, *]] =
    new ResourceTracing[F] {
      protected def combine[A](traceR: Resource[F, _], r: Resource[F, A]): Resource[F, A] = r <* traceR
    }

  def creationTracing[F[_]: Sync](implicit t: Tracer, setup: TracingSetup): Tracing[Resource[F, *], Resource[F, *]] = {
    implicit val defer = deferResource[F]
    Tracing.tracingDeferMonadError[Resource[F, *]]
  }

  def spanAndScopeResource[F[_]](
    span: => Span,
    scope: Option[Span => F[Scope]],
    beforeClose: (Span, ExitCase[Throwable]) => F[Unit]
  )(implicit sync: Sync[F]): Resource[F, (Span, Option[Scope])] = {
    import sync.delay
    Resource.makeCase(
      for {
        sp <- delay(span)
        sc <- scope.traverse(_(sp))
      } yield (sp, sc)
    ) {
      case ((span, scope), exit) =>
        beforeClose(span, exit)
          .guarantee(delay{ util.finishSpanSafe(span) } *>
                     delay{ scope.foreach(util.closeScopeSafe) })
    }
  }

  private abstract class ResourceTracing[F[_]: Sync](implicit t: Tracer, setup: TracingSetup) extends Tracing.Endo[Resource[F, *]] {
    protected def noTrace: Resource[F, *] ~> Resource[F, *] = FunctionK.id
    protected def build(spanBuilder: Tracer.SpanBuilder, activate: Boolean): Resource[F, *] ~> Resource[F, *] =
      λ[Resource[F, *] ~> Resource[F, *]] { combine(spanAndScopeResource1(spanBuilder, activate), _)}

    protected def combine[A](traceR: Resource[F, _], r: Resource[F, A]): Resource[F, A]
  }

  private[opentracing] def spanAndScopeResource1[F[_]](spanBuilder: Tracer.SpanBuilder, activate: Boolean)
                                                      (implicit sync: Sync[F], tracer: Tracer, setup: TracingSetup) = {
    import sync._
    spanAndScopeResource(
      setup.beforeStart(spanBuilder).start(),
      if (activate) Some((s: Span) => delay(tracer.activateSpan(s))) else None,
      (span, exit) => delay(setup.beforeStop(exitCaseToEither(exit))(span))
    )
  }

  private def exitCaseToEither(exit: ExitCase[Throwable]) = exit match {
    case ExitCase.Completed => Right(())
    case ExitCase.Error(e)  => Left(e)
    case ExitCase.Canceled  => Left(new Exception("Canceled"))
  }

  private def deferResource[F[_]: Defer: Applicative]: Defer[Resource[F, *]] =
    new Defer[Resource[F, *]] {
      def defer[A](fa: => Resource[F, A]): Resource[F, A] = Resource.suspend(util.cats.defer[F](fa))
    }
}
