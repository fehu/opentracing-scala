package com.gihub.fehu.opentracing

import scala.collection.convert.DecorateAsScala

import io.opentracing.mock.{ MockSpan, MockTracer }
import io.opentracing.util.GlobalTracer
import org.scalatest.{ BeforeAndAfter, Matchers, Suite }

trait Spec extends Matchers with BeforeAndAfter with DecorateAsScala {
  this: Suite =>

  val mockTracer = new MockTracer()

  before {
    mockTracer.reset()
    GlobalTracer.register(mockTracer)
  }

  def finishedSpans(): Seq[MockSpan] = mockTracer.finishedSpans().asScala
}
