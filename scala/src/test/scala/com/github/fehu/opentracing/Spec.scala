package com.github.fehu.opentracing

import scala.collection.JavaConverters._

import _root_.io.opentracing.mock.{ MockSpan, MockTracer }
import org.scalatest.{ BeforeAndAfter, Suite }
import org.scalatest.matchers.should.Matchers

trait Spec extends Matchers with BeforeAndAfter {
  this: Suite =>

  implicit lazy val mockTracer: MockTracer = new MockTracer

  before {
    mockTracer.reset()
  }

  type TestedSpan = Spec.TestedSpan
  val TestedSpan: Spec.TestedSpan.type = Spec.TestedSpan

  def finishedMockSpans(): Seq[MockSpan] = mockTracer.finishedSpans().asScala.toSeq
  def finishedSpans(): Seq[Spec.TestedSpan] = finishedMockSpans().map(TestedSpan(_))
}

object Spec {
  case class TestedSpan(
    traceId: Long,
    spanId: Long,
    parentId: Long,
    operationName: String,
    tags: Map[String, AnyRef] = Map(),
    logs: List[Map[String, Any]] = Nil
  )
  object TestedSpan {
    def apply(mock: MockSpan): TestedSpan = TestedSpan(
      traceId = mock.context.traceId,
      spanId = mock.context.spanId,
      parentId = mock.parentId,
      operationName = mock.operationName,
      tags = mock.tags.asScala.toMap,
      logs = mock.logEntries().asScala.map(_.fields().asScala.toMap).toList
    )
  }
}
