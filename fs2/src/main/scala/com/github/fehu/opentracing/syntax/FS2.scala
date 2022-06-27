package com.github.fehu.opentracing.syntax

import _root_.fs2.Stream
import cats.~>
import cats.effect.MonadCancelThrow
import cats.syntax.apply._
import io.opentracing.SpanContext
import io.opentracing.propagation.Format

import com.github.fehu.opentracing.Traced
import com.github.fehu.opentracing.propagation.{ Propagation, PropagationCompanion }

object FS2 {

  final implicit class TracedFs2StreamOps[F[_]: MonadCancelThrow, A](stream: Stream[F, A])(implicit t: Traced[F]) {

    // // // Trace Entire Stream // // //

    def traceLifetime(operation: String, tags: Traced.Tag*): Stream[F, A] =
      tracingLifetime(t, operation, tags)

    def traceLifetimeInjecting(ctx: SpanContext)
                              (operation: String, tags: Traced.Tag*): Stream[F, A] =
      tracingLifetime(t.injectContext(ctx), operation, tags)

    def traceLifetimeInjectingOpt(
      ctx: Option[SpanContext],
      traceEmpty: Boolean = true,
      emptyOrphan: Boolean = true
    )(
      operation: String,
      tags: Traced.Tag*
    ): Stream[F, A] =
      tracingLifetimeOpt(ctx.map(t.injectContext), operation, tags, traceEmpty, emptyOrphan)

    def traceLifetimeInjectingFrom[C](format: Format[C])(carrier: C)
                                     (operation: String, tags: Traced.Tag*): Stream[F, A] =
      tracingLifetime(t.injectContextFrom(format)(carrier), operation, tags)

    def traceLifetimeInjectingFromOpt[C](
      format: Format[C]
    )(
      carrier: Option[C],
      traceEmpty: Boolean = true,
      emptyOrphan: Boolean = true
    )(
      operation: String,
      tags: Traced.Tag*
    ): Stream[F, A] =
      tracingLifetimeOpt(carrier.map(t.injectContextFrom(format)), operation, tags, traceEmpty, emptyOrphan)

    def traceLifetimeInjectingPropagated[C <: Propagation](carrier: C)
                                                          (operation: String, tags: Traced.Tag*)
                                                          (implicit companion: PropagationCompanion[C]): Stream[F, A] =
      traceLifetimeInjectingFrom(companion.format)(carrier.underlying)(operation, tags: _*)

    def traceLifetimeInjectingPropagatedOpt[C <: Propagation](
      carrier: Option[C],
      traceEmpty: Boolean = true,
      emptyOrphan: Boolean = true
    )(
      operation: String,
      tags: Traced.Tag*
    )(implicit
      companion: PropagationCompanion[C]
    ): Stream[F, A] =
      traceLifetimeInjectingFromOpt(companion.format)(carrier.map(_.underlying), traceEmpty, emptyOrphan)(operation, tags: _*)

    // // // Trace Elements Usage // // //

    def traceUsage(operation: String, tags: Traced.Tag*): Stream[F, A] =
      tracingElems(_ => t, _ => _.span(operation, tags: _*))

    def traceUsage(trace: A => Traced.Operation.Builder): Stream[F, A] =
      tracingElems(_ => t, trace)

    def traceUsageInjecting(context: A => SpanContext, trace: A => Traced.Operation.Builder): Stream[F, A] =
      tracingElems(a => t.injectContext(context(a)), trace)

    def traceUsageInjectingOpt(
      context: A => Option[SpanContext],
      trace: A => Traced.Operation.Builder,
      traceEmpty: Boolean = true,
      emptyOrphan: Boolean = true
    ): Stream[F, A] =
      tracingElemsOpt(context(_).map(t.injectContext), trace, traceEmpty, emptyOrphan)

    def traceUsageInjectingFrom[C](format: Format[C])(carrier: A => C, trace: A => Traced.Operation.Builder): Stream[F, A] =
      tracingElems(a => t.injectContextFrom(format)(carrier(a)), trace)

    def traceUsageInjectingFromOpt[C](format: Format[C])(
      carrier: A => Option[C],
      trace: A => Traced.Operation.Builder,
      traceEmpty: Boolean = true,
      emptyOrphan: Boolean = true
    ): Stream[F, A] =
      tracingElemsOpt(carrier(_).map(t.injectContextFrom(format)), trace, traceEmpty, emptyOrphan)

    def traceUsageInjectingPropagated[C <: Propagation](carrier: A => C, trace: A => Traced.Operation.Builder)
                                                       (implicit companion: PropagationCompanion[C]): Stream[F, A] =
      traceUsageInjectingFrom(companion.format)(carrier andThen (_.underlying), trace)

    def traceUsageInjectingPropagatedOpt[C <: Propagation](
      carrier: A => Option[C],
      trace: A => Traced.Operation.Builder,
      traceEmpty: Boolean = true,
      emptyOrphan: Boolean = true
    )(implicit
      companion: PropagationCompanion[C]
    ): Stream[F, A] =
      traceUsageInjectingFromOpt(companion.format)(carrier andThen (_.map(_.underlying)), trace, traceEmpty, emptyOrphan)

    // // // Log Elements // // //

    def tracedLog(f: A => Seq[(String, Any)]): Stream[F, A] =
      stream.evalTap(a => t.currentSpan.log(f(a): _*))

    def tracedElemLog: Stream[F, A] = stream.evalTap(t.currentSpan log _.toString)

    // // // Helpers // // //

    private def tracingLifetime(i: Traced.Interface[F], op: String, tags: Seq[Traced.Tag]): Stream[F, A] =
      for {
        (span, finish) <- Stream eval i.spanResource(op, tags: _*).allocated
        a <- stream.translate(λ[F ~> F](t.recoverCurrentSpan(span) *> _))
                   .onFinalize(finish)
      } yield a

    private def tracingLifetimeOpt(i: Option[Traced.Interface[F]], op: String, tags: Seq[Traced.Tag], traceEmpty: Boolean, emptyOrphan: Boolean): Stream[F, A] = {
      lazy val iw =
        if (traceEmpty) Some(if (emptyOrphan) t.withoutParent else t)
        else None
      i.orElse(iw).map(tracingLifetime(_, op, tags)).getOrElse(stream)
    }

    private def tracingElems(f: A => Traced.Interface[F], trace: A => Traced.Operation.Builder): Stream[F, A] =
      stream.flatMap(a => Stream.resource(f(a).spanResource(trace(a))).as(a))

    private def tracingElemsOpt(f: A => Option[Traced.Interface[F]], trace: A => Traced.Operation.Builder, traceEmpty: Boolean, emptyOrphan: Boolean): Stream[F, A] =
      stream.flatMap { a =>
        lazy val iw =
          if (traceEmpty) Some(if (emptyOrphan) t.withoutParent else t)
          else None
        f(a).orElse(iw).fold(
          Stream.emit(a).covary[F]
        )(
          i => Stream.resource(i.spanResource(trace(a))).as(a)
        )
      }
  }

}
