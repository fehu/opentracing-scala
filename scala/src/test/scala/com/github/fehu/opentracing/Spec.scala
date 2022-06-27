package com.github.fehu.opentracing

import scala.collection.JavaConverters.*

import _root_.io.opentracing.mock.{ MockSpan, MockTracer }
import org.scalatest.{ BeforeAndAfter, BeforeAndAfterAll, Suite }
import org.scalatest.matchers.should.Matchers

trait Spec extends Matchers with BeforeAndAfter with BeforeAndAfterAll {
  this: Suite =>

  implicit lazy val mockTracer: MockTracer = new MockTracer

  before {
    mockTracer.reset()
  }

  type TestedSpan = Spec.TestedSpan
  val TestedSpan: Spec.TestedSpan.type = Spec.TestedSpan

  def finishedSpans(): Seq[Spec.TestedSpan] = mockTracer.finishedSpans().asScala.toSeq.map(Spec.normalizedSpan)
}

object Spec {
  case class TestedSpan(
    spanId: Long,
    parentId: Long,
    operationName: String,
    tags: Map[String, AnyRef] = Map(),
    logs: List[Map[String, Any]] = Nil
  )

  protected def normalizedSpan(mock: MockSpan): TestedSpan = {
    val traceId = mock.context.traceId
    TestedSpan(
      spanId        = mock.context.spanId - traceId,
      parentId      = if (mock.parentId == 0) 0 else mock.parentId - traceId,
      operationName = mock.operationName,
      tags          = mock.tags.asScala.toMap,
      logs          = mock.logEntries().asScala.map(_.fields().asScala.toMap).toList
    )
  }
}
