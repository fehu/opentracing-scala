package com.gihub.fehu

import scala.language.{ higherKinds, implicitConversions }

import cats.{ Eval, Id, Later, ~> }
import io.opentracing.{ Span, Tracer }
import io.opentracing.util.GlobalTracer

package object opentracing {

  def trace(implicit tracing: Tracing[Later, Eval], tracer: Tracer): TraceLaterEval.Ops = new TraceLaterEval.Ops
  object TraceLaterEval {

    final class Builder[F[_]](f: Later ~> F) {
      def apply[A](a: => A): F[A] = f(Later(a))
      def map[R[_]](g: F ~> R): Builder[R] = new Builder[R](g compose f)
    }

    final class Ops(implicit val tracing: Tracing[Later, Eval], tracer: Tracer) {
      def later: Tracing.Interface[Builder[Eval]] = tracing.transform.map(new Builder(_))
      def now: Tracing.Interface[Builder[Id]] = later.map(_.map(λ[Eval ~> Id](_.value)))
    }
  }

  implicit class TraceOps[F0[_], A, F1[_]](fa: F0[A])(implicit val trace: Tracing[F0, F1], tracer: Tracer) {
    def tracing: trace.PartiallyApplied[A] = trace(fa)
  }

  object Implicits {
    implicit def defaultTracerOpt: Option[Tracer] = Option(GlobalTracer.get())
    implicit def activeSpanOpt(implicit tracerOpt: Option[Tracer]): Option[Span] = tracerOpt.flatMap(Option apply _.activeSpan())
  }

  object NullableImplicits {
    object Tracer extends LowPriorityNullableTracerImplicits {
      implicit def nullableTracerFromOption(implicit opt: Option[Tracer]): Tracer = opt.orNull
    }
    trait LowPriorityNullableTracerImplicits {
      implicit def defaultNullableTracer: Tracer = Implicits.defaultTracerOpt.orNull
    }


    object Span {
      implicit def activeNullableSpan(implicit tracer: Tracer): Span = Implicits.activeSpanOpt(Option(tracer)).orNull
    }
  }

}
