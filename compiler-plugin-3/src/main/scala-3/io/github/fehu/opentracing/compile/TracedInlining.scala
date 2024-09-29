package io.github.fehu.opentracing.compile

import dotty.tools.dotc.transform.Inlining
import io.opentracing.Tracer

class TracedInlining(tracer: Tracer) extends Inlining, TracedPhase(tracer)
