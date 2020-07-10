package com.github.fehu.opentracing.internal

import cats.Endo
import cats.effect.Sync

import com.github.fehu.opentracing.Traced
import io.opentracing.{ Span, SpanContext, Tracer }

private[opentracing] object Tools {
  def newSpan[F[_]](tracer: Tracer,
                    parent: Option[Either[Span, SpanContext]],
                    buildHook: Endo[Tracer.SpanBuilder],
                    op: String,
                    tags: Seq[Traced.Tag])
                   (implicit sync: Sync[F]): F[Span] = {
    val b0 = tracer.buildSpan(op).ignoreActiveSpan
    val b1 = parent.map(_.fold(b0.asChildOf, b0.asChildOf)).getOrElse(b0)
    val b2 = tags.foldLeft(b1){ case (b, t) => t.apply(b) }
    sync.delay{ buildHook(b2).start() }
  }
}
