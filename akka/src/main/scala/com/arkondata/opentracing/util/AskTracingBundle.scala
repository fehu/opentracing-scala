package com.arkondata.opentracing.util

import com.arkondata.opentracing.Tracing.TracingSetup
import com.arkondata.opentracing.akka.AskTracing
import io.opentracing.Tracer

final case class AskTracingBundle[F[_]](ask: AskTracing[F], setup: TracingSetup, tracer: Tracer) {
  object Implicits {
    implicit def bundleAskTracing: AskTracing[F] = ask
    implicit def bundleTracer: Tracer = tracer
    implicit def bundleTracingSetup: TracingSetup = setup
  }
}
object AskTracingBundle {
  implicit def mkAskTracingBundle[F[_]](implicit ask: AskTracing[F], setup: TracingSetup, tracer: Tracer): AskTracingBundle[F] =
    AskTracingBundle(ask, setup, tracer)
}
