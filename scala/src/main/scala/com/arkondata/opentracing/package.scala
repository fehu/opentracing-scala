package com.arkondata

import scala.jdk.CollectionConverters._

import cats.{ Eval, Later }
import io.opentracing.{ Span, Tracer }

package object opentracing {

  def trace(implicit tracing: Tracing[Later, Eval], tracer: Tracer): TraceLaterEval.Ops = new TraceLaterEval.Ops

  implicit class TraceOps[F0[_], A, F1[_]](fa: F0[A])(implicit val trace: Tracing[F0, F1], tracer: Tracer) {
    def tracing: trace.PartiallyApplied[A] = trace(fa)
  }

  implicit class SpanOps(span: => Span) extends SpanLog(() => span) {
    def log(fields: (String, Any)*): Span = span.log(fields.toMap.asJava)
  }


  def activate[R](span: Span)(r: => R)(implicit tracer: Tracer): R = {
    val scope = util.safe(span)(tracer.scopeManager().activate)
    try r
    finally scope.foreach(util.closeScopeSafe)
  }

  implicit class ActivateOps[F[_], A](fa: F[A])(implicit activate: Activating[F]) {
    def activating(span: Span): F[A] = activate(span)(fa)
  }

}
