package com.github.fehu.opentracing

import scala.collection.convert.DecorateAsScala

import io.opentracing.mock.{ MockSpan, MockTracer }
import io.opentracing.util.ThreadLocalScopeManager
import org.scalatest.{ BeforeAndAfter, Suite }
import org.scalatest.matchers.should.Matchers

trait Spec extends Matchers with BeforeAndAfter with DecorateAsScala {
  this: Suite =>

  implicit val mockTracer = new MockTracer(new ThreadLocalScopeManager)

  before {
    mockTracer.reset()
    mockTracer.scopeManager().activate(null)
  }

  def finishedSpans(): Seq[MockSpan] = mockTracer.finishedSpans().asScala
}
