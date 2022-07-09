package io.github.fehu.opentracing.internal

import cats.effect.Sync
import cats.Endo
import io.opentracing.{ Span, SpanContext, Tracer }

import io.github.fehu.opentracing.Traced
import io.github.fehu.opentracing.internal.compat.*

private[opentracing] object Tools {

  def newSpan[F[_]: Sync](tracer: Tracer,
                          parent: Option[Either[Span, SpanContext]],
                          buildHook: Endo[Tracer.SpanBuilder],
                          op: String,
                          tags: Seq[Traced.Tag]): F[Span] = {
    val b0 = tracer.buildSpan(op).nn.ignoreActiveSpan.nn
    val b1 = parent.map(_.fold(b0.asChildOf, b0.asChildOf).nn).getOrElse(b0)
    val b2 = tags.foldLeft(b1){ case (b, t) => t.apply(b) }
    Sync[F].delay(buildHook(b2).start().nn)
  }
}
