package com.github.fehu.opentracing.v2.internal

import cats.{ Defer, Endo, MonadError }
import io.opentracing.{ Span, SpanContext, Tracer }

import com.github.fehu.opentracing.v2.Traced

private[opentracing] object Tools {

  def delay[F[_]]: Delay[F] = Delay.asInstanceOf[Delay[F]]

  class Delay[F[_]] {
    def apply[A](a: => A)(implicit D: Defer[F], M: MonadError[F, Throwable]): F[A] = D.defer(M.pure(a))
  }
  private object Delay extends Delay[cats.Id]

  def newSpan[F[_]](tracer: Tracer,
                    parent: Option[Either[Span, SpanContext]],
                    buildHook: Endo[Tracer.SpanBuilder],
                    op: String,
                    tags: Seq[Traced.Tag])
                   (implicit D: Defer[F], M: MonadError[F, Throwable]): F[Span] = {
    val b0 = tracer.buildSpan(op).ignoreActiveSpan
    val b1 = parent.map(_.fold(b0.asChildOf, b0.asChildOf)).getOrElse(b0)
    val b2 = tags.foldLeft(b1){ case (b, t) => t.apply(b) }
    D.defer(M.pure{ buildHook(b2).start() })
  }
}
