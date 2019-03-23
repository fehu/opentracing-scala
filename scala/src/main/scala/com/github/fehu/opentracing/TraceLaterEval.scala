package com.github.fehu.opentracing

import scala.language.higherKinds

import cats.{ Eval, Id, Later, ~> }
import io.opentracing.Tracer

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
