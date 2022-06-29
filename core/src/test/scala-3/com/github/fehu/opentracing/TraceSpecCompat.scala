package com.github.fehu.opentracing

import com.github.fehu.opentracing.Spec.TestedSpan

object TraceSpecCompat {
  def expectedTraceTimeoutsTestOverride: Seq[TestedSpan] => Seq[TestedSpan] = _.map {
    case span if span.operationName == "f2" => span.copy(spanId = span.spanId + 1)
    case span if span.operationName == "f3" => span.copy(spanId = span.spanId - 1)
    case span                               => span
  }
}
