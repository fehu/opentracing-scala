package io.github.fehu.opentracing

import io.github.fehu.opentracing.Spec.TestedSpan

object TraceSpecCompat {
  def expectedTraceTimeoutsTestOverride: Seq[TestedSpan] => Seq[TestedSpan] = locally
}
