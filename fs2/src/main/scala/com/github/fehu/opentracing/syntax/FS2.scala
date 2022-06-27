package com.github.fehu.opentracing.syntax

import _root_.fs2.Stream
import cats.~>
import cats.effect.MonadCancelThrow
import cats.syntax.apply.*
import io.opentracing.SpanContext

import com.github.fehu.opentracing.Traced
import com.github.fehu.opentracing.internal.compat.FK
import com.github.fehu.opentracing.propagation.Propagation

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

    def traceLifetimeInjectingFrom(carrier: Propagation#Carrier)
                                  (operation: String, tags: Traced.Tag*): Stream[F, A] =
      tracingLifetime(t.injectContextFrom(carrier), operation, tags)

    def traceLifetimeInjectingFromOpt(
      carrier: Option[Propagation#Carrier],
      traceEmpty: Boolean = true,
      emptyOrphan: Boolean = true
    )(
      operation: String,
      tags: Traced.Tag*
    ): Stream[F, A] =
      tracingLifetimeOpt(carrier.map(t.injectContextFrom), operation, tags, traceEmpty, emptyOrphan)

    def traceLifetimeInjectingPropagated(carrier: Propagation#Carrier)
                                        (operation: String, tags: Traced.Tag*): Stream[F, A] =
      traceLifetimeInjectingFrom(carrier)(operation, tags*)

    def traceLifetimeInjectingPropagatedOpt(
      carrier: Option[Propagation#Carrier],
      traceEmpty: Boolean = true,
      emptyOrphan: Boolean = true
    )(
      operation: String,
      tags: Traced.Tag*
    ): Stream[F, A] =
      traceLifetimeInjectingFromOpt(carrier, traceEmpty, emptyOrphan)(operation, tags*)

    // // // Trace Elements Usage // // //

    def traceUsage(operation: String, tags: Traced.Tag*): Stream[F, A] =
      tracingElems(_ => t, _ => _.span(operation, tags*))

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

    def traceUsageInjectingFrom(carrier: A => Propagation#Carrier, trace: A => Traced.Operation.Builder): Stream[F, A] =
      tracingElems(a => t.injectContextFrom(carrier(a)), trace)

    def traceUsageInjectingFromOpt(
      carrier: A => Option[Propagation#Carrier],
      trace: A => Traced.Operation.Builder,
      traceEmpty: Boolean = true,
      emptyOrphan: Boolean = true
    ): Stream[F, A] =
      tracingElemsOpt(carrier(_).map(t.injectContextFrom), trace, traceEmpty, emptyOrphan)

    def traceUsageInjectingPropagated(carrier: A => Propagation#Carrier, trace: A => Traced.Operation.Builder): Stream[F, A] =
      traceUsageInjectingFrom(carrier, trace)

    def traceUsageInjectingPropagatedOpt(
      carrier: A => Option[Propagation#Carrier],
      trace: A => Traced.Operation.Builder,
      traceEmpty: Boolean = true,
      emptyOrphan: Boolean = true
    ): Stream[F, A] =
      traceUsageInjectingFromOpt(carrier, trace, traceEmpty, emptyOrphan)

    // // // Log Elements // // //

    def tracedLog(f: A => Seq[(String, Any)]): Stream[F, A] =
      stream.evalTap(a => t.currentSpan.log(f(a)*))

    def tracedElemLog: Stream[F, A] = stream.evalTap(t.currentSpan log _.toString)

    // // // Helpers // // //

    private def tracingLifetime(i: Traced.Interface[F], op: String, tags: Seq[Traced.Tag]): Stream[F, A] =
      for {
        (span, finish) <- Stream eval i.spanResource(op, tags*).allocated
        a <- stream.translate(FK.lift[F, F](t.recoverCurrentSpan(span) *> _))
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
