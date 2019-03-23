package com.github.fehu

import scala.collection.JavaConverters._
import scala.language.higherKinds

import cats.{ Eval, Later }
import io.opentracing.{ Span, Tracer }

package object opentracing {

  def trace(implicit tracing: Tracing[Later, Eval], tracer: Tracer): TraceLaterEval.Ops = new TraceLaterEval.Ops

  implicit class TraceOps[F0[_], A, F1[_]](fa: F0[A])(implicit val trace: Tracing[F0, F1], tracer: Tracer) {
    def tracing: trace.PartiallyApplied[A] = trace(fa)
  }

  implicit class SpanOps(span: Span) {
    def log(fields: (String, Any)*): Span = span.log(fields.toMap.asJava)
  }
}
