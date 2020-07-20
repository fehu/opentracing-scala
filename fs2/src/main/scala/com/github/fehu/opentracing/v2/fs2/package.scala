package com.github.fehu.opentracing.v2

import _root_.fs2.Stream
import cats.Functor
import cats.syntax.apply._
import io.opentracing.SpanContext
import io.opentracing.propagation.Format

package object fs2 {

  final implicit class TracedFs2StreamOps[F[_]: Functor, A](stream: Stream[F, A])(implicit t: Traced[F]) {
    def trace(operation: String, tags: Traced.Tag*): Stream[F, A] =
      Stream.resource(t.spanResource(operation, tags: _*)) *> stream

    def inject(context: SpanContext)(operation: String, tags: Traced.Tag*): Stream[F, A] =
      Stream.resource(t.injectContext(context).spanResource(operation, tags: _*)) *> stream

    def inject(context: Option[SpanContext])(operation: String, tags: Traced.Tag*): Stream[F, A] =
      context.map(inject(_)(operation, tags: _*)).getOrElse(stream)

    def injectFrom[C](format: Format[C])(carrier: C)(operation: String, tags: Traced.Tag*): Stream[F, A] =
      Stream.resource(t.injectContextFrom(format)(carrier).spanResource(operation, tags: _*)) *> stream

    def injectFromOpt[C](format: Format[C])(carrier: Option[C])(operation: String, tags: Traced.Tag*): Stream[F, A] =
      carrier.map(injectFrom(format)(_)(operation, tags: _*)).getOrElse(stream)

    def tracedLog(f: A => Seq[(String, Any)]): Stream[F, A] =
      stream.evalTap(a => t.currentSpan.log(f(a): _*))

    def tracedElemLog: Stream[F, A] = stream.evalTap(t.currentSpan log _.toString)
  }

}
