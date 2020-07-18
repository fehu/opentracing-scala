package com.github.fehu.opentracing.v2

import _root_.fs2.Stream
import cats.Functor
import cats.syntax.apply._

package object fs2 {

  final implicit class TracedFs2StreamOps[F[_]: Functor, A](stream: Stream[F, A])(implicit t: Traced[F]) {
    def traced(operation: String, tags: Traced.Tag*): Stream[F, A] =
      Stream.resource(t.spanResource(operation, tags: _*)) *> stream

    def tracedLog(f: A => Seq[(String, Any)]): Stream[F, A] =
      stream.evalTap(a => t.currentSpan.log(f(a): _*))

    def tracedElemLog: Stream[F, A] = stream.evalTap(t.currentSpan log _.toString)
  }

}
