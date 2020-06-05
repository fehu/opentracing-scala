package com.arkondata.opentracing

import scala.jdk.CollectionConverters._

import io.opentracing.mock.{ MockSpan, MockTracer }
import io.opentracing.util.ThreadLocalScopeManager
import org.scalatest.{ BeforeAndAfter, Suite }
import org.scalatest.matchers.should.Matchers

trait Spec extends Matchers with BeforeAndAfter {
  this: Suite =>

  implicit val mockTracer = new MockTracer(new ThreadLocalScopeManager)

  before {
    mockTracer.reset()
    mockTracer.scopeManager().activate(null)
  }

  def finishedSpans(): Seq[MockSpan] = mockTracer.finishedSpans().asScala.toSeq
}
