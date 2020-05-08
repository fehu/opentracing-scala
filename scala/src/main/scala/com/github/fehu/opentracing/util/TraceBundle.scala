package com.github.fehu.opentracing.util

import com.github.fehu.opentracing.Tracing
import com.github.fehu.opentracing.Tracing.TracingSetup
import io.opentracing.Tracer

final case class TraceBundle[F0[_], F1[_]](tracing: Tracing[F0, F1], setup: TracingSetup, tracer: Tracer) {
  object Implicits {
    implicit def bundleTracing: Tracing[F0, F1] = tracing
    implicit def bundleTracer: Tracer = tracer
    implicit def bundleTracingSetup: TracingSetup = setup
  }
}
object TraceBundle {
  type Endo[F[_]] = TraceBundle[F, F]

  implicit def mkTraceBundle[F0[_], F1[_]](implicit tracing: Tracing[F0, F1], setup: TracingSetup, tracer: Tracer): TraceBundle[F0, F1] =
    TraceBundle(tracing, setup, tracer)
}
