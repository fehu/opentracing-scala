package com.github.fehu.opentracing

import _root_.fs2.Stream
import cats.~>
import cats.effect.{ Bracket, Resource }
import cats.syntax.apply._
import io.opentracing.SpanContext
import io.opentracing.propagation.Format

import com.github.fehu.opentracing.Traced.ActiveSpan

package object fs2 {

  final implicit class TracedFs2StreamOps[F[_]: Bracket[*[_], Throwable], A](stream: Stream[F, A])(implicit t: Traced[F]) {
    def trace(operation: String, tags: Traced.Tag*): Stream[F, A] =
      common(_.spanResource(operation, tags: _*))

    def inject(context: SpanContext)(operation: String, tags: Traced.Tag*): Stream[F, A] =
      common(_.injectContext(context).spanResource(operation, tags: _*))

    def inject(context: Option[SpanContext])(operation: String, tags: Traced.Tag*): Stream[F, A] =
      context.map(inject(_)(operation, tags: _*)).getOrElse(stream)

    def injectFrom[C](format: Format[C])(carrier: C)(operation: String, tags: Traced.Tag*): Stream[F, A] =
      common(_.injectContextFrom(format)(carrier).spanResource(operation, tags: _*))

    def injectFromOpt[C](format: Format[C])(carrier: Option[C])(operation: String, tags: Traced.Tag*): Stream[F, A] =
      carrier.map(injectFrom(format)(_)(operation, tags: _*)).getOrElse(stream)

    def tracedLog(f: A => Seq[(String, Any)]): Stream[F, A] =
      stream.evalTap(a => t.currentSpan.log(f(a): _*))

    def tracedElemLog: Stream[F, A] = stream.evalTap(t.currentSpan log _.toString)

    private def common(f: Traced[F] => Resource[F, ActiveSpan]) =
      for {
        (span, finish) <- Stream eval f(t).allocated
        a <- stream.translate(λ[F ~> F](t.recoverCurrentSpan(span) *> _))
                   .onFinalize(finish)
      } yield a
  }

}
