package com.github.fehu.opentracing

import com.github.fehu.opentracing.Spec.TestedSpan

object TraceSpecCompat {
  def expectedTraceTimeoutsTestOverride: Seq[TestedSpan] => Seq[TestedSpan] = locally
}
